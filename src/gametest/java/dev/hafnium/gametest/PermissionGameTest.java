package dev.hafnium.gametest;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.permission.CapabilityStatus;
import dev.hafnium.client.core.permission.Environment;
import dev.hafnium.client.core.permission.PermissionNotices;
import dev.hafnium.client.core.permission.ServerProfile;
import dev.hafnium.client.core.task.StopReason;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskContext;
import dev.hafnium.client.core.task.TaskHandle;
import dev.hafnium.client.core.task.TaskResource;
import dev.hafnium.client.core.task.TaskState;
import dev.hafnium.client.core.world.WorldScope;
import dev.hafnium.client.modules.automation.OperatingModeModule;
import dev.hafnium.client.modules.client.CapabilitiesModule;
import dev.hafnium.client.modules.client.ServerProfileModule;
import dev.hafnium.client.net.AutomationPolicyPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * The permission architecture in a real game: singleplayer, a real dedicated server without any Hafnium plugin, the
 * same server sending an explicit grant and revoking it mid-task, disconnecting with tasks scheduled, reconnecting to
 * a different server, and switching operating modes through the GUI module.
 */
public final class PermissionGameTest implements FabricClientGameTest {
    private final Checks checks = new Checks();
    private ClientGameTestContext ctx;
    private HafniumCore core;

    /** Endless task with configurable capabilities, autonomy, resource and scope. */
    static final class Probe implements Task {
        final String name;
        final Set<Capability> caps;
        final Autonomy autonomy;
        final TaskResource resource;
        final WorldScope scope;
        final List<StopReason> stops = new ArrayList<>();
        volatile int ticks;

        Probe(String name, Set<Capability> caps, Autonomy autonomy, TaskResource resource, WorldScope scope) {
            this.name = name;
            this.caps = caps;
            this.autonomy = autonomy;
            this.resource = resource;
            this.scope = scope;
        }

        static Probe of(String name, Capability cap, Autonomy autonomy, TaskResource resource) {
            return new Probe(name, Set.of(cap), autonomy, resource, WorldScope.NONE);
        }

        /** Client-local work: no capabilities. */
        static Probe local(String name, TaskResource resource, WorldScope scope) {
            return new Probe(name, Set.of(), Autonomy.AUTONOMOUS, resource, scope);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Set<TaskResource> resources() {
            return resource == null ? Set.of() : Set.of(resource);
        }

        @Override
        public Set<Capability> capabilities() {
            return caps;
        }

        @Override
        public Autonomy autonomy() {
            return autonomy;
        }

        @Override
        public WorldScope worldScope() {
            return scope;
        }

        @Override
        public boolean tick(TaskContext c) {
            ticks++;
            return false;
        }

        @Override
        public void stop(TaskContext c, StopReason reason) {
            stops.add(reason);
        }
    }

    private TaskHandle submit(Probe p) {
        return ctx.computeOnClient(mc -> core.scheduler().submit(p));
    }

    private TaskState state(TaskHandle h) {
        return ctx.computeOnClient(mc -> h.state());
    }

    private void setMode(String label) {
        ctx.runOnClient(mc -> core.modules().get(OperatingModeModule.class).mode.set(label));
    }

    private OperatingMode mode() {
        return ctx.computeOnClient(mc -> core.control().mode());
    }

    private CapabilityStatus.Level level(Capability c) {
        return ctx.computeOnClient(mc -> core.permissions().status(c).level());
    }

    private boolean noticed(String fragment) {
        return ctx.computeOnClient(mc -> {
            for (PermissionNotices.Notice n : core.notices().recent()) {
                if (n.text().contains(fragment)) {
                    return true;
                }
            }
            return false;
        });
    }

    private void cancelAll() {
        ctx.runOnClient(mc -> core.scheduler().cancelAll("scenario done"));
        ctx.waitTick();
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        this.ctx = context;
        this.core = ctx.computeOnClient(mc -> HafniumCore.get());
        // Profiles persist in the run directory; start every run from the safe defaults.
        ctx.runOnClient(mc -> core.profiles().load(ServerProfile.DEFAULTS, List.of()));
        ctx.runOnClient(mc -> core.modules().get(OperatingModeModule.class).mode.set("Manual"));

        singleplayerAndModeSwitching();
        Path profileFile = ctx.computeOnClient(mc -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("hafnium").resolve("server-profiles.json"));
        String firstServer = multiplayerWithoutPluginThenGrantRevokeAndDisconnect();
        checks.check(Files.isRegularFile(profileFile) && read(profileFile).contains("\"" + firstServer + "\""),
                "the edited server profile was saved to disk", profileFile);
        reconnectToAnotherServer(firstServer);
        checks.finish();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ 1 + 7

    private void singleplayerAndModeSwitching() {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(10);
            Environment env = ctx.computeOnClient(mc -> core.permissions().environment());
            checks.check(env.kind() == Environment.Kind.INTEGRATED_HOST, "[1] singleplayer is an integrated host", env);
            checks.check(mode() == OperatingMode.MANUAL, "[1] a new world starts in Manual mode", mode());
            checks.check(ctx.computeOnClient(mc -> core.permissions().serverPolicy()) == null,
                    "[1] no server authorization was involved", "");

            Probe local = Probe.local("plan", null, WorldScope.NONE);
            TaskHandle lh = submit(local);
            ctx.waitTicks(3);
            checks.check(state(lh) == TaskState.RUNNING, "[1] client-local tasks run even in Manual mode", state(lh));

            Probe autoManual = Probe.of("auto-in-manual", Capability.MOVEMENT, Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
            TaskHandle am = submit(autoManual);
            ctx.waitTicks(2);
            checks.check(state(am) == TaskState.FAILED && am.error().contains("Manual mode"),
                    "[7] Manual mode refuses gameplay tasks with a clear reason", am.error());

            setMode("Autonomous");
            checks.check(mode() == OperatingMode.AUTONOMOUS, "[7] the Operating Mode setting switches the mode", mode());
            Probe auto = Probe.of("auto", Capability.MOVEMENT, Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
            Probe assist = Probe.of("assist", Capability.LOOK, Autonomy.ASSISTED, TaskResource.LOOK);
            TaskHandle ah = submit(auto);
            TaskHandle sh = submit(assist);
            ctx.waitTicks(3);
            checks.check(state(ah) == TaskState.RUNNING && state(sh) == TaskState.RUNNING,
                    "[1] hosting authorizes autonomous and assisted tasks without any grant", state(ah) + " / " + state(sh));
            boolean cheats = ctx.computeOnClient(mc -> core.permissions().privileges().operatorCommands());
            checks.check(level(Capability.COMBAT_PLAYERS) == CapabilityStatus.Level.AUTONOMOUS
                            && level(Capability.OPERATOR_COMMANDS) == (cheats ? CapabilityStatus.Level.AUTONOMOUS : CapabilityStatus.Level.UNAVAILABLE)
                            && level(Capability.CREATIVE_INVENTORY) == CapabilityStatus.Level.UNAVAILABLE,
                    "[1] indicator: hosting authorizes automation; op/creative follow the game's own privileges",
                    "cheats=" + cheats + " " + ctx.computeOnClient(mc -> core.permissions().statuses().get(Capability.OPERATOR_COMMANDS)));
            ctx.takeScreenshot("hafnium-capabilities-singleplayer");

            setMode("Assist");
            ctx.waitTick();
            checks.check(state(ah) == TaskState.FAILED && auto.stops.contains(StopReason.NOT_PERMITTED)
                            && ah.error().contains("needs Autonomous mode"),
                    "[7] switching to Assist stops autonomous tasks", ah.error());
            checks.check(state(sh) == TaskState.RUNNING, "[7] assisted tasks keep running in Assist mode", state(sh));
            checks.check(ctx.computeOnClient(mc -> core.scheduler().holderOf(TaskResource.MOVEMENT)) == null,
                    "[7] the stopped task's resource is released", "");
            setMode("Manual");
            ctx.waitTick();
            checks.check(state(sh) == TaskState.FAILED && assist.stops.contains(StopReason.NOT_PERMITTED),
                    "[7] switching to Manual stops assisted tasks", sh.error());
            checks.check(state(lh) == TaskState.RUNNING, "[7] client-local work survives every mode switch", state(lh));
            checks.check(noticed("Mode: Assist") && noticed("Mode: Manual"), "[7] mode changes are announced", "");
            cancelAll();
        }
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------------ 2, 3, 4, 5

    private String multiplayerWithoutPluginThenGrantRevokeAndDisconnect() {
        String serverId;
        TaskHandle running;
        Probe runningProbe;
        TaskHandle queued;
        TaskHandle worldBound;
        TaskHandle planning;
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer();
             TestServerConnection conn = server.connect()) {
            conn.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);
            Environment env = ctx.computeOnClient(mc -> core.permissions().environment());
            serverId = ServerProfile.normalize(env.serverId());
            checks.check(env.kind() == Environment.Kind.REMOTE, "[2] dedicated server is a REMOTE environment", env);
            checks.check(ctx.computeOnClient(mc -> core.permissions().serverPolicy()) == null,
                    "[2] the server has no Hafnium plugin and sent no policy", "");
            checks.check(mode() == OperatingMode.MANUAL, "[2] joining starts in Manual mode", mode());
            checks.check(noticed("with no Hafnium authorization") && noticed("cannot prevent penalties"),
                    "[2] a warning explains what is unavailable and that server rules still apply", "");

            // Client-side features keep working without any server integration.
            int chunks = ctx.computeOnClient(mc -> core.world().current() == null ? 0 : core.world().current().loadedChunkCount());
            checks.check(chunks > 20, "[2] world observation works on the plain server", chunks + " chunks");
            boolean hud = ctx.computeOnClient(mc -> core.modules().get(CapabilitiesModule.class).isEnabled());
            String headline = ctx.computeOnClient(mc -> CapabilitiesModule.headline(core.permissions()));
            checks.check(hud && headline.contains("no policy"), "[2] the capability indicator is visible", headline);
            Probe plan = Probe.local("plan", null, WorldScope.NONE);
            planning = submit(plan);
            ctx.waitTicks(2);
            checks.check(state(planning) == TaskState.RUNNING, "[2] client-local tasks run without any grant", state(planning));

            setMode("Autonomous");
            Probe autoMove = Probe.of("auto-move", Capability.MOVEMENT, Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
            TaskHandle amh = submit(autoMove);
            Probe assistMove = Probe.of("assist-move", Capability.MOVEMENT, Autonomy.ASSISTED, TaskResource.MOVEMENT);
            TaskHandle smh = submit(assistMove);
            ctx.waitTicks(2);
            checks.check(state(amh) == TaskState.FAILED && amh.attempts() == 0 && amh.error().contains("requires authorization"),
                    "[2] autonomous gameplay never starts without authorization", amh.error());
            checks.check(state(smh) == TaskState.FAILED && smh.attempts() == 0 && smh.error().contains("Server Profile"),
                    "[2] assistance stays off until the profile confirms the rules allow it", smh.error());
            checks.check(noticed("auto-move unavailable:"), "[2] the refusal is shown to the user", "");

            ServerProfileModule profile = ctx.computeOnClient(mc -> core.modules().get(ServerProfileModule.class));
            ctx.runOnClient(mc -> profile.assistanceAllowed.set(true));
            Probe move = Probe.of("assist-move", Capability.MOVEMENT, Autonomy.ASSISTED, TaskResource.MOVEMENT);
            Probe look = Probe.of("assist-look", Capability.LOOK, Autonomy.ASSISTED, TaskResource.LOOK);
            TaskHandle mh = submit(move);
            TaskHandle lh = submit(look);
            ctx.waitTicks(3);
            checks.check(state(mh) == TaskState.RUNNING && state(lh) == TaskState.RUNNING,
                    "[2] after confirming the server's rules, assisted tasks run", state(mh) + " / " + state(lh));
            checks.check(level(Capability.MOVEMENT) == CapabilityStatus.Level.ASSIST_ONLY
                            && level(Capability.COMBAT_PLAYERS) == CapabilityStatus.Level.UNAVAILABLE,
                    "[2] indicator: assist only, player combat off", ctx.computeOnClient(mc -> core.permissions().statuses().toString()));
            ctx.takeScreenshot("hafnium-capabilities-remote-noplugin");

            ctx.runOnClient(mc -> profile.prohibitSetting(Capability.MOVEMENT).set(true));
            checks.check(state(mh) == TaskState.FAILED && move.stops.contains(StopReason.NOT_PERMITTED)
                            && mh.error().contains("prohibited"),
                    "[2] marking a capability prohibited stops its tasks immediately", mh.error());
            checks.check(state(lh) == TaskState.RUNNING, "[2] other capabilities are not disabled with it", state(lh));
            ctx.runOnClient(mc -> profile.prohibitSetting(Capability.MOVEMENT).set(false));
            ctx.runOnClient(mc -> lh.cancel("scenario done")); // keep the planning task for scenario 5

            // Vanilla privileges come from the server itself.
            String player = ctx.computeOnClient(mc -> mc.player.getName().getString());
            server.runCommand("op " + player);
            ctx.waitTicks(5);
            boolean opped = ctx.computeOnClient(mc -> core.permissions().privileges().operatorCommands());
            checks.check(opped && level(Capability.OPERATOR_COMMANDS) == CapabilityStatus.Level.ASSIST_ONLY,
                    "[2] operator status reported by the vanilla server enables op commands (assisted only)", opped);
            server.runCommand("deop " + player);
            ctx.waitTicks(5);
            checks.check(level(Capability.OPERATOR_COMMANDS) == CapabilityStatus.Level.UNAVAILABLE,
                    "[2] de-op removes it again", level(Capability.OPERATOR_COMMANDS));

            // 3. explicit grant
            sendPolicy(server, List.of("movement", "look"));
            ctx.waitTicks(3);
            checks.check(noticed(env.serverId() + " authorized: movement, look"), "[3] the grant is announced", "");
            Probe grantedMove = Probe.of("granted-move", Capability.MOVEMENT, Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
            Probe grantedLook = Probe.of("granted-look", Capability.LOOK, Autonomy.AUTONOMOUS, TaskResource.LOOK);
            TaskHandle gm = submit(grantedMove);
            TaskHandle gl = submit(grantedLook);
            ctx.waitTicks(3);
            checks.check(state(gm) == TaskState.RUNNING && state(gl) == TaskState.RUNNING,
                    "[3] with an explicit grant, autonomous tasks run", state(gm) + " / " + state(gl));
            checks.check(level(Capability.MOVEMENT) == CapabilityStatus.Level.AUTONOMOUS
                            && level(Capability.INVENTORY) == CapabilityStatus.Level.UNAVAILABLE,
                    "[3] indicator: granted capabilities are Auto, unlisted ones are Off", level(Capability.INVENTORY));
            ctx.takeScreenshot("hafnium-capabilities-remote-granted");

            // 4. revocation during execution
            sendPolicy(server, List.of("look"));
            ctx.waitTicks(3);
            checks.check(state(gm) == TaskState.FAILED && grantedMove.stops.contains(StopReason.NOT_PERMITTED),
                    "[4] revoking movement stops the running task", gm.error());
            checks.check(ctx.computeOnClient(mc -> core.scheduler().holderOf(TaskResource.MOVEMENT)) == null,
                    "[4] its resource lock is released", "");
            checks.check(state(gl) == TaskState.RUNNING, "[4] the still-granted task keeps running", state(gl));
            checks.check(noticed("granted-move stopped:"), "[4] the user is told why it stopped", "");

            // 5. disconnect while tasks are scheduled
            runningProbe = grantedLook;
            running = gl;
            queued = submit(Probe.of("queued-look", Capability.LOOK, Autonomy.AUTONOMOUS, TaskResource.LOOK));
            worldBound = submit(Probe.local("world-bound", null, WorldScope.dimension("minecraft:overworld")));
            ctx.waitTicks(2);
            checks.check(state(queued) == TaskState.READY, "[5] a second task is queued behind the resource holder", state(queued));
        }
        ctx.waitTicks(10);
        checks.check(state(running) == TaskState.FAILED || state(running) == TaskState.CANCELLED,
                "[5] the running gameplay task stopped on disconnect", state(running) + " " + running.error());
        checks.check(!runningProbe.stops.isEmpty(), "[5] it was stopped exactly once (resources released)", runningProbe.stops);
        checks.check(queued.isDone() && queued.attempts() == 0,
                "[5] the queued task never started", state(queued) + " " + queued.error());
        checks.check(state(worldBound) == TaskState.CANCELLED, "[5] world-scoped work is cancelled", state(worldBound));
        checks.check(state(planning) == TaskState.RUNNING, "[5] client-local work outside the world keeps running", state(planning));
        checks.check(ctx.computeOnClient(mc -> core.scheduler().holderOf(TaskResource.LOOK)) == null,
                "[5] no resource lock survives the disconnect", "");
        Environment after = ctx.computeOnClient(mc -> core.permissions().environment());
        checks.check(after.kind() == Environment.Kind.NONE && ctx.computeOnClient(mc -> core.permissions().serverPolicy()) == null,
                "[5] environment and server grant are cleared", after);
        cancelAll();
        return serverId;
    }

    private static void sendPolicy(TestDedicatedServerContext server, List<String> caps) {
        server.runOnServer(s -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, new AutomationPolicyPayload(AutomationPolicyPayload.VERSION, caps));
            }
        });
    }

    // ------------------------------------------------------------------ 6

    private void reconnectToAnotherServer(String firstServer) {
        Properties props = new Properties();
        props.setProperty("server-port", "25566");
        try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props);
             TestServerConnection conn = server.connect()) {
            conn.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);
            Environment env = ctx.computeOnClient(mc -> core.permissions().environment());
            String id = ServerProfile.normalize(env.serverId());
            checks.check(env.kind() == Environment.Kind.REMOTE && !id.equals(firstServer),
                    "[6] reconnected to a different server", firstServer + " -> " + id);
            checks.check(ctx.computeOnClient(mc -> core.permissions().serverPolicy()) == null
                            && !ctx.computeOnClient(mc -> core.permissions().privileges().operatorCommands()),
                    "[6] nothing from the previous server carries over", "");
            checks.check(mode() == OperatingMode.MANUAL, "[6] the new connection starts in Manual mode", mode());
            boolean assistHere = ctx.computeOnClient(mc -> core.permissions().profile().assistanceAllowed());
            boolean assistThere = ctx.computeOnClient(mc -> core.profiles().forServer(firstServer).assistanceAllowed());
            boolean shown = ctx.computeOnClient(mc -> core.modules().get(ServerProfileModule.class).assistanceAllowed.get());
            checks.check(!assistHere && assistThere && !shown,
                    "[6] each server has its own profile (and the GUI shows this server's)", assistHere + "/" + assistThere + "/" + shown);

            setMode("Assist");
            TaskHandle h = submit(Probe.of("assist-move", Capability.MOVEMENT, Autonomy.ASSISTED, TaskResource.MOVEMENT));
            ctx.waitTicks(2);
            checks.check(state(h) == TaskState.FAILED && h.error().contains(env.serverId()),
                    "[6] the first server's confirmation does not apply here", h.error());
            int chunks = ctx.computeOnClient(mc -> core.world().current() == null ? 0 : core.world().current().loadedChunkCount());
            checks.check(chunks > 20, "[6] observation follows the new connection", chunks + " chunks");
            setMode("Manual");
        }
        ctx.waitTicks(5);
    }
}
