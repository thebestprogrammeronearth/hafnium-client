package dev.hafnium.client.modules.automation;

import dev.hafnium.client.anubis.chat.ModuleChat;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.navigation.CollisionSnapshot;
import dev.hafnium.client.core.navigation.NavigationTask;
import dev.hafnium.client.core.navigation.PathPlanner;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.task.StopReason;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskContext;
import dev.hafnium.client.core.task.TaskException;
import dev.hafnium.client.core.task.TaskHandle;
import dev.hafnium.client.core.task.TaskOptions;
import dev.hafnium.client.core.task.TaskResource;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.LayoutElement;
import dev.hafnium.client.modules.world.BaseFinderModule;
import dev.hafnium.client.modules.world.WorldScannerModule;
import dev.hafnium.client.navigation.MinecraftNavigationDriver;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Auto Base Hunt: walks to the chunks Base Finder and WorldScanner flag, nearest first, and when there are none,
 * explores outward in a square spiral. It is an autonomous task, so it only runs where Hafnium's permission layer
 * allows autonomous movement (your own world, or a server that authorizes it); everywhere else it refuses with the
 * reason. Movement uses the Navigation module's hazard-aware walking planner in short legs.
 */
public final class AutoBaseHuntModule extends Module {
    private static final int LEG = 12;

    private final KeybindSetting toggleKey = setting(new KeybindSetting("Start or Stop"));
    private final ActionSetting startStop = setting(new ActionSetting("Start / Stop", this::toggle));
    private final ModeSetting pattern = setting(new ModeSetting("Pattern", "Candidates, then Spiral", "Candidates, then Spiral", "Candidates Only", "Spiral Only"));
    private final NumberSetting spiralStep = setting(new NumberSetting("Spiral Step", 160, 48, 512, 16, " b"));
    private final NumberSetting maxDistance = setting(new NumberSetting("Max Distance", 3000, 200, 30000, 100, " b"));
    private final BoolSetting stopAtCandidate = setting(new BoolSetting("Stop At Candidate", true));
    private final LayoutElement hud = new LayoutElement("auto-base-hunt", "Auto Base Hunt", () -> isEnabled() && this.status != null, 0.5f, 0f, 0f, 4f);

    private ModuleContext context;
    private TaskHandle running;
    private String status;
    private final LongSet visited = new LongOpenHashSet();

    public AutoBaseHuntModule() {
        super("Auto Base Hunt", Category.AUTOMATION,
                "Walks to chunks flagged by Base Finder and WorldScanner, then spirals outward. Autonomous: only where automation is authorized.");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        context = ctx;
        ctx.subscribe(GameKeyEvent.class, e -> {
            if (e.key() != KeybindSetting.NONE && e.key() == toggleKey.get()) {
                toggle();
            }
        });
        ctx.subscribe(HudRenderEvent.class, this::renderHud);
    }

    @Override
    protected void onDisable() {
        if (running != null && !running.isDone()) {
            running.cancel("Auto Base Hunt disabled");
        }
        context = null;
        status = null;
    }

    public TaskHandle running() {
        return running;
    }

    public String status() {
        return status;
    }

    public void toggle() {
        if (running != null && !running.isDone()) {
            running.cancel("Stopped by player");
            return;
        }
        if (context == null) {
            ModuleChat.send(name(), ModuleChat.body().warn("Turn Auto Base Hunt on first."));
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        status = "Starting";
        running = context.submitTask(new Hunt(player.getBlockX(), player.getBlockZ()), TaskOptions.DEFAULT);
    }

    /** Next target: the nearest unvisited flagged chunk, else the next spiral point. Returns {x, z, isCandidate}. */
    private int[] nextCandidate(int px, int pz, int originX, int originZ) {
        if (pattern.is("Spiral Only")) {
            return null;
        }
        LongSet flagged = new LongOpenHashSet();
        var modules = HafniumCore.get().modules();
        BaseFinderModule base = modules.get(BaseFinderModule.class);
        WorldScannerModule scanner = modules.get(WorldScannerModule.class);
        if (base.isEnabled()) {
            flagged.addAll(base.flaggedChunks());
        }
        if (scanner.isEnabled()) {
            flagged.addAll(scanner.flaggedChunks());
        }
        double limit = maxDistance.get();
        int[] best = null;
        double bestSq = Double.MAX_VALUE;
        for (long key : flagged) {
            if (visited.contains(key)) {
                continue;
            }
            int x = ChunkPos.getX(key) * 16 + 8, z = ChunkPos.getZ(key) * 16 + 8;
            if (Math.hypot(x - originX, z - originZ) > limit) {
                continue;
            }
            double d = (double) (x - px) * (x - px) + (double) (z - pz) * (z - pz);
            if (d < bestSq) {
                bestSq = d;
                best = new int[]{x, z, 1};
            }
        }
        return best;
    }

    private final class Hunt implements Task {
        private final int originX, originZ;
        private NavigationTask leg;
        private int[] target;
        private int spiralIndex;
        private int failures;
        private double lastDistance = Double.MAX_VALUE;
        private int noProgressLegs;

        Hunt(int originX, int originZ) {
            this.originX = originX;
            this.originZ = originZ;
        }

        @Override
        public String name() {
            return "Auto Base Hunt";
        }

        @Override
        public Set<TaskResource> resources() {
            return Set.of(TaskResource.MOVEMENT, TaskResource.LOOK);
        }

        @Override
        public Set<Capability> capabilities() {
            return Set.of(Capability.MOVEMENT, Capability.LOOK);
        }

        @Override
        public Autonomy autonomy() {
            return Autonomy.AUTONOMOUS;
        }

        /** Square spiral around the origin: (step, 0), (step, step), (0, step), (-step, step), ... */
        private int[] spiralPoint(int n) {
            int step = spiralStep.getInt();
            int x = 0, z = 0, dx = 1, dz = 0, segment = 1, walked = 0, turns = 0;
            for (int i = 0; i < n + 1; i++) {
                x += dx;
                z += dz;
                if (++walked == segment) {
                    walked = 0;
                    int t = dx;
                    dx = -dz;
                    dz = t;
                    if (++turns % 2 == 0) {
                        segment++;
                    }
                }
            }
            return new int[]{originX + x * step, originZ + z * step, 0};
        }

        private int[] pickTarget(LocalPlayer p) {
            int[] candidate = nextCandidate(p.getBlockX(), p.getBlockZ(), originX, originZ);
            if (candidate != null) {
                return candidate;
            }
            if (pattern.is("Candidates Only")) {
                return null;
            }
            while (true) {
                int[] s = spiralPoint(spiralIndex);
                if (Math.hypot(s[0] - originX, s[1] - originZ) > maxDistance.get()) {
                    return null;
                }
                if (Math.hypot(s[0] - p.getX(), s[1] - p.getZ()) > 24) {
                    return s;
                }
                spiralIndex++;
            }
        }

        private void dropLeg(TaskContext c) {
            if (leg != null) {
                leg.suspend(c);
                leg = null;
            }
        }

        @Override
        public boolean tick(TaskContext c) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) {
                throw new TaskException("Not in a world");
            }
            if (target == null || target[2] == 0 && nextCandidate(p.getBlockX(), p.getBlockZ(), originX, originZ) != null) {
                dropLeg(c);
                target = pickTarget(p);
                lastDistance = Double.MAX_VALUE;
                noProgressLegs = 0;
                if (target == null) {
                    status = "Done: nothing left within " + maxDistance.getInt() + " blocks";
                    ModuleChat.send(AutoBaseHuntModule.this.name(), ModuleChat.body().text(status));
                    return true;
                }
            }
            double dist = Math.hypot(target[0] + 0.5 - p.getX(), target[1] + 0.5 - p.getZ());
            status = (target[2] == 1 ? "Candidate" : "Exploring") + " " + target[0] + ", " + target[1] + " · " + Math.round(dist) + "m · "
                    + visited.size() + " checked";
            c.status(status);
            if (dist < (target[2] == 1 ? 12 : 16)) {
                dropLeg(c);
                if (target[2] == 1) {
                    visited.add(ChunkPos.asLong(target[0] >> 4, target[1] >> 4));
                    ModuleChat.send(AutoBaseHuntModule.this.name(), ModuleChat.body().good("Reached candidate").text(" at ")
                            .coords(target[0], p.getBlockY(), target[1]));
                    if (stopAtCandidate.get()) {
                        status = "Stopped at candidate " + target[0] + ", " + target[1];
                        return true;
                    }
                } else {
                    spiralIndex++;
                }
                target = null;
                return false;
            }
            if (leg == null) {
                if (dist > lastDistance - 2) {
                    noProgressLegs++;
                }
                lastDistance = Math.min(lastDistance, dist);
                if (noProgressLegs > 6) {
                    ModuleChat.send(AutoBaseHuntModule.this.name(), ModuleChat.body().warn("Can't make progress towards ")
                            .coords(target[0], p.getBlockY(), target[1]).text(", skipping it."));
                    if (target[2] == 1) {
                        visited.add(ChunkPos.asLong(target[0] >> 4, target[1] >> 4));
                    } else {
                        spiralIndex++;
                    }
                    target = null;
                    return false;
                }
                double angle = Math.atan2(target[1] + 0.5 - p.getZ(), target[0] + 0.5 - p.getX()) + (failures % 2 == 0 ? 1 : -1) * (failures + 1) / 2 * Math.PI / 5;
                double step = Math.min(LEG, dist);
                int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
                var bounds = new CollisionSnapshot.Bounds(x - 15, y - 4, z - 15, x + 16, y + 6, z + 16);
                var driver = new MinecraftNavigationDriver(HafniumCore.get(), bounds);
                var from = driver.position();
                var goal = new PathPlanner.Point(x + (int) Math.round(Math.cos(angle) * step), from.y() + (failures / 2 % 3 == 1 ? 1 : failures / 2 % 3 == 2 ? -1 : 0),
                        z + (int) Math.round(Math.sin(angle) * step));
                leg = new NavigationTask(driver, goal, new PathPlanner.Costs(1, 2, 20000), Autonomy.AUTONOMOUS);
                try {
                    leg.start(c);
                } catch (Exception e) {
                    leg = null;
                    failures++;
                }
                return false;
            }
            try {
                if (leg.tick(c)) {
                    dropLeg(c);
                    failures = 0;
                }
            } catch (TaskException e) {
                dropLeg(c);
                failures++;
            }
            return false;
        }

        @Override
        public void suspend(TaskContext c) {
            dropLeg(c);
        }

        @Override
        public void stop(TaskContext c, StopReason reason) {
            dropLeg(c);
            if (reason == StopReason.NOT_PERMITTED || reason == StopReason.FAILED) {
                status = "Stopped: " + c.handle().status();
                ModuleChat.send(AutoBaseHuntModule.this.name(), ModuleChat.body().warn("Stopped: ").text(c.handle().status()));
            } else if (reason == StopReason.CANCELLED) {
                status = "Stopped";
            }
        }
    }

    private void renderHud(HudRenderEvent e) {
        if (status == null || Minecraft.getInstance().options.hideGui) {
            return;
        }
        String text = "Auto Base Hunt · " + status;
        float w = GuiRenderUtil.textWidth(FontRole.SMALL, text) + 12f;
        ThemeManager.sync();
        hud.begin(e.graphics());
        try {
            GuiRenderUtil.roundRect(0, 0, w, 13f, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.82f));
            GuiRenderUtil.roundRect(0, 0, 2f, 13f, 1f, running != null && !running.isDone() ? 0xFF7BE0A0 : ThemeManager.TEXT_SECONDARY);
            GuiRenderUtil.text(FontRole.SMALL, text, 7f, 6.5f, ThemeManager.TEXT);
            hud.size(w, 13f);
        } finally {
            hud.end();
        }
    }
}
