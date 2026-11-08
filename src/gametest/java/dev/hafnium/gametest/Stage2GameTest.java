package dev.hafnium.gametest;

import dev.hafnium.client.HafniumClient;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.permission.Environment;
import dev.hafnium.client.core.task.StopReason;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskContext;
import dev.hafnium.client.core.task.TaskHandle;
import dev.hafnium.client.core.task.TaskResource;
import dev.hafnium.client.core.task.TaskState;
import dev.hafnium.client.core.world.BlockKind;
import dev.hafnium.client.core.world.ChunkTerrain;
import dev.hafnium.client.core.world.DimensionModel;
import dev.hafnium.client.core.world.EntityKind;
import dev.hafnium.client.core.world.HazardType;
import dev.hafnium.client.core.world.ObservedBlock;
import dev.hafnium.client.core.world.Pos;
import dev.hafnium.client.core.world.WorldScope;
import dev.hafnium.client.modules.world.ObservationModule;
import dev.hafnium.client.net.AutomationPolicyPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stage 2 in a real game: world observation from server data, tasks on real client ticks, invalidation by chunk
 * unload / dimension change / disconnect, emergency stop, and permission restriction on an integrated server. The
 * multiplayer permission scenarios are in {@link PermissionGameTest}.
 */
public final class Stage2GameTest implements FabricClientGameTest {
    private final Checks checks = new Checks();
    private ClientGameTestContext ctx;

    /** Endless task that records the thread it ran on and how it was stopped. */
    static final class Probe implements Task {
        final Set<Capability> caps;
        final WorldScope scope;
        final List<StopReason> stops = new ArrayList<>();
        volatile Thread ranOn;
        volatile int ticks;

        Probe(Set<Capability> caps, WorldScope scope) {
            this.caps = caps;
            this.scope = scope;
        }

        @Override
        public String name() {
            return "probe";
        }

        @Override
        public Set<TaskResource> resources() {
            return Set.of(TaskResource.MOVEMENT);
        }

        @Override
        public Set<Capability> capabilities() {
            return caps;
        }

        @Override
        public WorldScope worldScope() {
            return scope;
        }

        @Override
        public boolean tick(TaskContext c) {
            ranOn = Thread.currentThread();
            ticks++;
            return false;
        }

        @Override
        public void stop(TaskContext c, StopReason reason) {
            stops.add(reason);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        this.ctx = context;
        HafniumCore core = ctx.computeOnClient(mc -> HafniumCore.get());
        Probe worldTask;
        TaskHandle worldHandle;
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(10);
            Environment env = ctx.computeOnClient(mc -> core.permissions().environment());
            checks.check(env.kind() == Environment.Kind.INTEGRATED_HOST, "singleplayer is an integrated host", env);
            // Joining always starts in MANUAL; the probes below exercise autonomous gameplay capabilities.
            ctx.runOnClient(mc -> core.control().setMode(OperatingMode.AUTONOMOUS));

            observeBlocksTerrainEntities(sp, core);
            tasksOnRealTicks(core);
            permissionRestrictionAndServerPolicyPacket(sp, core);
            observationLimitsFromModule(core);
            chunkUnloadAndDimensionInvalidation(sp, core);
            emergencyStopKey(core);

            // A world-scoped task that must be cancelled by the disconnect below.
            worldTask = new Probe(Set.of(), WorldScope.dimension("minecraft:overworld"));
            worldHandle = ctx.computeOnClient(mc -> core.scheduler().submit(worldTask));
            ctx.waitTicks(2);
        }
        ctx.waitTicks(5);
        checks.check(worldHandle.state() == TaskState.CANCELLED, "disconnect cancels world-dependent tasks",
                worldHandle.state() + " " + worldHandle.error());
        checks.check(worldTask.stops.contains(StopReason.CANCELLED), "disconnected task was stopped (resources released)", worldTask.stops);
        Environment after = ctx.computeOnClient(mc -> core.permissions().environment());
        checks.check(after.kind() == Environment.Kind.NONE, "permission environment is cleared on disconnect", after);
        checks.check(ctx.computeOnClient(mc -> core.world().current()) == null, "world model is cleared on disconnect", "");

        checks.finish();
    }

    private void observeBlocksTerrainEntities(TestSingleplayerContext sp, HafniumCore core) {
        int pending = ctx.waitFor(mc -> HafniumClient.observer().pendingScans() == 0, 200);
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        int x = p[0] + 3;
        int y = p[1];
        int z = p[2];
        DimensionModel dim = ctx.computeOnClient(mc -> core.world().current());
        checks.check(dim != null && dim.id().equals("minecraft:overworld"), "world model follows the overworld", dim == null ? null : dim.id());
        ChunkTerrain terrain = ctx.computeOnClient(mc -> dim.terrain(x >> 4, z >> 4));
        checks.check(terrain != null && terrain.maxSurfaceY() == y - 1 && terrain.voidColumns() == 0,
                "chunk terrain matches the flat world surface (top block below the player's feet)", terrain + " feet y=" + y);
        int loaded = ctx.computeOnClient(mc -> dim.loadedChunkCount());
        double avg = ctx.computeOnClient(mc -> HafniumClient.observer().averageScanMicros());
        double max = ctx.computeOnClient(mc -> HafniumClient.observer().maxTickScanMillis());
        checks.check(loaded > 50, "loaded chunks were scanned", loaded + " chunks, avg " + String.format("%.1f", avg)
                + " µs/chunk, max " + String.format("%.2f", max) + " ms/tick, waited " + pending + " ticks");

        sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " minecraft:diamond_ore");
        sp.getServer().runCommand("setblock " + (x + 2) + " " + y + " " + z + " minecraft:chest");
        sp.getServer().runCommand("setblock " + (x + 4) + " " + (y - 1) + " " + z + " minecraft:farmland");
        sp.getServer().runCommand("setblock " + (x + 4) + " " + y + " " + z + " minecraft:wheat[age=7]");
        sp.getServer().runCommand("setblock " + (x + 5) + " " + (y - 1) + " " + z + " minecraft:farmland");
        sp.getServer().runCommand("setblock " + (x + 5) + " " + y + " " + z + " minecraft:wheat[age=2]");
        sp.getServer().runCommand("setblock " + x + " " + y + " " + (z + 3) + " minecraft:magma_block");
        ctx.waitTicks(4);
        ObservedBlock ore = ctx.computeOnClient(mc -> dim.blockAt(x, y, z));
        ObservedBlock chest = ctx.computeOnClient(mc -> dim.blockAt(x + 2, y, z));
        ObservedBlock ripe = ctx.computeOnClient(mc -> dim.blockAt(x + 4, y, z));
        ObservedBlock young = ctx.computeOnClient(mc -> dim.blockAt(x + 5, y, z));
        ObservedBlock magma = ctx.computeOnClient(mc -> dim.blockAt(x, y, z + 3));
        checks.check(ore != null && ore.kind() == BlockKind.ORE && ore.blockId().equals("minecraft:diamond_ore"), "placed diamond ore is observed as ORE", ore);
        checks.check(chest != null && chest.kind() == BlockKind.CONTAINER, "placed chest is observed as CONTAINER", chest);
        checks.check(ripe != null && ripe.kind() == BlockKind.CROP && ripe.mature(), "wheat age 7 is a mature CROP", ripe);
        checks.check(young != null && young.kind() == BlockKind.CROP && !young.mature(), "wheat age 2 is an immature CROP", young);
        checks.check(magma != null && magma.hazard() == HazardType.MAGMA, "magma block is a MAGMA hazard", magma);
        List<ObservedBlock> hazards = ctx.computeOnClient(mc -> dim.hazardsNear(x, y, z, 8));
        checks.check(hazards.size() == 1, "hazardsNear finds exactly the magma block", hazards);

        sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " minecraft:air");
        ctx.waitTicks(3);
        checks.check(ctx.computeOnClient(mc -> dim.blockAt(x, y, z)) == null, "mined ore disappears from the model", "");

        sp.getServer().runCommand("summon minecraft:cow " + (x + 6) + " " + y + " " + z + " {NoAI:1b}");
        sp.getServer().runCommand("summon minecraft:zombie " + (x + 6) + " " + y + " " + (z + 3) + " {NoAI:1b}");
        sp.getServer().runCommand("summon minecraft:item " + (x + 1) + " " + y + " " + (z - 2) + " {Item:{id:\"minecraft:diamond\",count:1}}");
        ctx.waitTicks(4);
        String kinds = ctx.computeOnClient(mc -> {
            StringBuilder sb = new StringBuilder();
            for (var e : dim.entitiesNear(null, x, y, z, 20)) {
                sb.append(e.typeId()).append('=').append(e.kind()).append(' ');
            }
            return sb.toString();
        });
        checks.check(kinds.contains("minecraft:cow=PASSIVE") && kinds.contains("minecraft:zombie=HOSTILE")
                && kinds.contains("minecraft:item=ITEM"), "summoned entities are observed with the right kinds", kinds);
        sp.getServer().runCommand("kill @e[type=minecraft:cow]");
        ctx.waitTicks(25);
        int cows = ctx.computeOnClient(mc -> (int) dim.entitiesNear(EntityKind.PASSIVE, x, y, z, 30).stream()
                .filter(e -> e.typeId().equals("minecraft:cow")).count());
        checks.check(cows == 0, "killed entity is removed from the model", cows);
        sp.getServer().runCommand("kill @e[type=!minecraft:player]");
    }

    private void tasksOnRealTicks(HafniumCore core) {
        Probe p = new Probe(Set.of(Capability.MOVEMENT), WorldScope.NONE);
        TaskHandle h = ctx.computeOnClient(mc -> core.scheduler().submit(p));
        ctx.waitTicks(10);
        Thread clientThread = ctx.computeOnClient(mc -> Thread.currentThread());
        checks.check(h.state() == TaskState.RUNNING && p.ticks >= 8, "task ticks with the client", h.state() + " ticks=" + p.ticks);
        checks.check(p.ranOn == clientThread, "tasks run on the client thread", p.ranOn);
        ctx.runOnClient(mc -> h.cancel("test done"));
        checks.check(h.state() == TaskState.CANCELLED && p.stops.equals(List.of(StopReason.CANCELLED)), "cancel stops the task", p.stops);
    }

    private void permissionRestrictionAndServerPolicyPacket(TestSingleplayerContext sp, HafniumCore core) {
        Probe p = new Probe(Set.of(Capability.MOVEMENT), WorldScope.NONE);
        TaskHandle h = ctx.computeOnClient(mc -> core.scheduler().submit(p));
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> core.permissions().setUserRestrictions(Set.of(Capability.MOVEMENT)));
        // No tick in between: revocation must already have stopped it.
        TaskState state = ctx.computeOnClient(mc -> h.state());
        checks.check(state == TaskState.FAILED && p.stops.contains(StopReason.NOT_PERMITTED),
                "restricting a capability stops running tasks immediately", state + " " + h.error());
        ctx.runOnClient(mc -> core.permissions().setUserRestrictions(Set.of()));

        // The policy channel works end to end (server -> client), even though singleplayer needs no grant.
        sp.getServer().runOnServer(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, new AutomationPolicyPayload(AutomationPolicyPayload.VERSION, List.of("look", "inventory")));
            }
        });
        ctx.waitTicks(3);
        var policy = ctx.computeOnClient(mc -> core.permissions().serverPolicy());
        checks.check(policy != null && policy.allowed().equals(Set.of(Capability.LOOK, Capability.INVENTORY)),
                "policy packet from the server is received and parsed", policy);
    }

    private void observationLimitsFromModule(HafniumCore core) {
        ctx.runOnClient(mc -> core.modules().get(ObservationModule.class).cachedChunks.set(512.0));
        int max = ctx.computeOnClient(mc -> core.world().limits().maxCachedChunks());
        checks.check(max == 512, "Observation settings change world model limits immediately", max);
        ctx.runOnClient(mc -> core.modules().get(ObservationModule.class).cachedChunks.set(4096.0));
    }

    private void chunkUnloadAndDimensionInvalidation(TestSingleplayerContext sp, HafniumCore core) {
        int[] c = ctx.computeOnClient(mc -> new int[]{mc.player.chunkPosition().x, mc.player.chunkPosition().z});
        Probe chunkTask = new Probe(Set.of(), WorldScope.chunks("minecraft:overworld", Set.of(Pos.chunkKey(c[0], c[1]))));
        TaskHandle ch = ctx.computeOnClient(mc -> core.scheduler().submit(chunkTask));
        Probe dimTask = new Probe(Set.of(), WorldScope.dimension("minecraft:overworld"));
        ctx.waitTicks(2);
        sp.getServer().runCommand("tp @a 20000 -60 20000");
        ctx.waitFor(mc -> !core.world().current().isChunkLoaded(c[0], c[1]), 400);
        ctx.waitTicks(2);
        checks.check(ch.state() == TaskState.FAILED && ch.error() != null && ch.error().contains("unloaded"),
                "unloading a task's chunk invalidates it", ch.state() + " " + ch.error());
        ChunkTerrain stale = ctx.computeOnClient(mc -> core.world().current().terrain(c[0], c[1]));
        checks.check(stale != null, "unloaded chunk stays cached (stale) within limits", stale);
        long silent = ctx.computeOnClient(mc -> HafniumClient.observer().silentUnloads());
        checks.check(silent > 0, "chunks dropped without an unload callback are detected", silent + " silent unloads");

        TaskHandle dh = ctx.computeOnClient(mc -> core.scheduler().submit(dimTask));
        ctx.waitTicks(2);
        sp.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 120 0");
        ctx.waitFor(mc -> core.world().current() != null && core.world().current().id().equals("minecraft:the_nether"), 400);
        ctx.waitTicks(3);
        checks.check(dh.state() == TaskState.FAILED && dh.error().contains("Left dimension"),
                "changing dimension invalidates tasks scoped to the old one", dh.state() + " " + dh.error());
        boolean overworldStale = ctx.computeOnClient(mc -> core.world().dimension("minecraft:overworld").loadedChunkCount() == 0);
        checks.check(overworldStale, "overworld observations are marked stale after leaving", "");
        sp.getServer().runCommand("execute in minecraft:overworld run tp @a 0 -60 0");
        ctx.waitFor(mc -> core.world().current().id().equals("minecraft:overworld"), 400);
        ctx.waitTicks(10);
    }

    private void emergencyStopKey(HafniumCore core) {
        ctx.runOnClient(mc -> core.control().setMode(OperatingMode.AUTONOMOUS));
        Probe p = new Probe(Set.of(Capability.MOVEMENT), WorldScope.NONE);
        TaskHandle h = ctx.computeOnClient(mc -> core.scheduler().submit(p));
        ctx.waitTicks(3);
        ctx.getInput().pressKey(GLFW.GLFW_KEY_END);
        ctx.waitTicks(2);
        checks.check(h.state() == TaskState.CANCELLED && p.stops.contains(StopReason.CANCELLED), "End key emergency-stops running tasks", h.state());
        checks.check(ctx.computeOnClient(mc -> core.control().mode()) == OperatingMode.MANUAL, "emergency stop drops to MANUAL", "");
        checks.check(ctx.computeOnClient(mc -> core.scheduler().holderOf(TaskResource.MOVEMENT)) == null, "emergency stop releases resources", "");
    }
}
