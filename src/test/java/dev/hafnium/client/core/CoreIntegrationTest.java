package dev.hafnium.client.core;

import dev.hafnium.client.core.control.ControlEvent;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.permission.Environment;
import dev.hafnium.client.core.permission.ServerPrivileges;
import dev.hafnium.client.core.permission.ServerProfile;
import dev.hafnium.client.core.task.TaskScheduler;
import dev.hafnium.client.core.task.AsyncResult;
import dev.hafnium.client.core.task.StopReason;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskContext;
import dev.hafnium.client.core.task.TaskHandle;
import dev.hafnium.client.core.task.TaskOptions;
import dev.hafnium.client.core.task.TaskResource;
import dev.hafnium.client.core.task.TaskState;
import dev.hafnium.client.core.world.Pos;
import dev.hafnium.client.core.world.WorldModelLimits;
import dev.hafnium.client.core.world.WorldScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The Stage 2 services wired together exactly as HafniumCore does it. */
class CoreIntegrationTest {
    HafniumCore core;

    @BeforeEach
    void setUp() {
        core = new HafniumCore(ClientThread.DIRECT, WorldModelLimits.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        core.shutdown();
    }

    static final class Endless implements Task {
        final Set<Capability> caps;
        final WorldScope scope;
        final Autonomy autonomy;
        final Set<TaskResource> resources;
        final List<StopReason> stops = new ArrayList<>();

        Endless(Set<Capability> caps, WorldScope scope) {
            this(caps, scope, Autonomy.AUTONOMOUS, Set.of(TaskResource.MOVEMENT));
        }

        Endless(Set<Capability> caps, Autonomy autonomy, TaskResource resource) {
            this(caps, WorldScope.NONE, autonomy, Set.of(resource));
        }

        Endless(Set<Capability> caps, WorldScope scope, Autonomy autonomy, Set<TaskResource> resources) {
            this.caps = caps;
            this.scope = scope;
            this.autonomy = autonomy;
            this.resources = resources;
        }

        @Override
        public Autonomy autonomy() {
            return autonomy;
        }

        @Override
        public String name() {
            return "endless";
        }

        @Override
        public Set<TaskResource> resources() {
            return resources;
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
        public boolean tick(TaskContext ctx) {
            return false;
        }

        @Override
        public void stop(TaskContext ctx, StopReason reason) {
            stops.add(reason);
        }
    }

    @Test
    void revokingPermissionStopsRunningTasksImmediately() {
        core.permissions().setEnvironment(new Environment(Environment.Kind.REMOTE, "srv"));
        core.control().setMode(OperatingMode.AUTONOMOUS);
        core.permissions().applyServerPolicy(List.of("movement"));
        Endless t = new Endless(Set.of(Capability.MOVEMENT), WorldScope.NONE);
        TaskHandle h = core.scheduler().submit(t);
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, h.state());
        core.permissions().applyServerPolicy(List.of()); // server revokes; no tick in between
        assertEquals(TaskState.FAILED, h.state());
        assertEquals(List.of(StopReason.NOT_PERMITTED), t.stops);
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
    }

    static final Environment REMOTE = new Environment(Environment.Kind.REMOTE, "vanilla.example.net");

    @Test
    void withoutAPluginAssistanceFollowsTheProfileAndAutomationIsRefused() {
        core.permissions().setEnvironment(REMOTE, new ServerProfile(REMOTE.serverId(), true, Set.of()));
        assertNull(core.permissions().serverPolicy());
        core.control().setMode(OperatingMode.AUTONOMOUS);
        Endless assisted = new Endless(Set.of(Capability.MOVEMENT, Capability.LOOK, Capability.BLOCK_INTERACTION,
                Capability.INVENTORY, Capability.COMBAT_MOBS), Autonomy.ASSISTED, TaskResource.MOVEMENT);
        Endless autonomous = new Endless(Set.of(Capability.BLOCK_INTERACTION), Autonomy.AUTONOMOUS, TaskResource.INTERACTION);
        TaskHandle a = core.scheduler().submit(assisted);
        TaskHandle b = core.scheduler().submit(autonomous);
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, a.state(), String.valueOf(a.error()));
        assertEquals(TaskState.FAILED, b.state());
        assertTrue(b.error().startsWith(TaskScheduler.NOT_PERMITTED_PREFIX + "autonomous block_interaction requires authorization"), b.error());
        assertEquals(0, b.attempts());
        assertNull(core.scheduler().holderOf(TaskResource.INTERACTION));
    }

    @Test
    void withoutAPluginAndWithoutProfileConfirmationNothingActsButPlanningRuns() {
        core.permissions().setEnvironment(REMOTE, core.profiles().forServer(REMOTE.serverId()));
        core.control().setMode(OperatingMode.ASSIST);
        TaskHandle h = core.scheduler().submit(new Endless(Set.of(Capability.MOVEMENT), Autonomy.ASSISTED, TaskResource.MOVEMENT));
        TaskHandle plan = core.scheduler().submit(new Endless(Set.of(), Autonomy.AUTONOMOUS, TaskResource.CHAT));
        core.scheduler().tick();
        assertEquals(TaskState.FAILED, h.state());
        assertTrue(h.error().contains("confirm in its Server Profile"), h.error());
        assertEquals(TaskState.RUNNING, plan.state(), "client-local work is never gated");
    }

    /** Planning-style work: no capabilities, async over an immutable snapshot, with no world and no server at all. */
    record Plan(List<Integer> costs) implements ImmutableSnapshot {
    }

    @Test
    void clientLocalTasksNeedNoPermissionAnywhere() throws Exception {
        for (Environment env : List.of(Environment.NONE, new Environment(Environment.Kind.REMOTE, "vanilla.example.net"))) {
            core.permissions().setEnvironment(env);
            AsyncResult<?>[] result = new AsyncResult<?>[1];
            TaskHandle h = core.scheduler().submit(new Task() {
                @Override
                public String name() {
                    return "plan";
                }

                @Override
                public boolean tick(TaskContext ctx) {
                    if (result[0] == null) {
                        result[0] = ctx.async(new Plan(List.of(3, 4, 5)), p -> p.costs().stream().mapToInt(i -> i).sum());
                    }
                    return result[0].isDone();
                }
            });
            for (int i = 0; i < 200 && !h.isDone(); i++) {
                core.scheduler().tick();
                Thread.sleep(1);
            }
            assertEquals(TaskState.SUCCEEDED, h.state(), env + ": " + h.error());
            assertEquals(12, result[0].get());
        }
    }

    @Test
    void playerCombatNeverStartsOnRemoteServersWithoutAGrant() {
        core.permissions().setEnvironment(new Environment(Environment.Kind.REMOTE, "srv"),
                new ServerProfile("srv", true, Set.of()));
        core.control().setMode(OperatingMode.AUTONOMOUS);
        TaskHandle h = core.scheduler().submit(new Endless(Set.of(Capability.MOVEMENT, Capability.COMBAT_PLAYERS), Autonomy.ASSISTED, TaskResource.MOVEMENT));
        core.scheduler().tick();
        assertEquals(TaskState.FAILED, h.state());
        assertTrue(h.error().contains("combat_players requires authorization from srv"), h.error());
        assertEquals(0, h.attempts());
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
    }

    @Test
    void losingAServerPrivilegeStopsPrivilegedTasksImmediately() {
        core.permissions().setEnvironment(new Environment(Environment.Kind.INTEGRATED_HOST, "local:x"));
        core.control().setMode(OperatingMode.AUTONOMOUS);
        TaskHandle denied = core.scheduler().submit(new Endless(Set.of(Capability.OPERATOR_COMMANDS), WorldScope.NONE));
        core.scheduler().tick();
        assertEquals(TaskState.FAILED, denied.state());
        assertTrue(denied.error().contains("operator permission"), denied.error());

        core.permissions().setPrivileges(new ServerPrivileges(true, false)); // server opped us
        Endless t = new Endless(Set.of(Capability.OPERATOR_COMMANDS), WorldScope.NONE);
        TaskHandle h = core.scheduler().submit(t);
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, h.state());
        core.permissions().setPrivileges(ServerPrivileges.NONE); // de-opped; no tick in between
        assertEquals(TaskState.FAILED, h.state());
        assertEquals(List.of(StopReason.NOT_PERMITTED), t.stops);
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
    }

    @Test
    void aServerPolicyCanSwitchOffAssistance() {
        core.permissions().setEnvironment(new Environment(Environment.Kind.REMOTE, "srv"), new ServerProfile("srv", true, Set.of()));
        core.control().setMode(OperatingMode.ASSIST);
        Endless t = new Endless(Set.of(Capability.MOVEMENT), Autonomy.ASSISTED, TaskResource.MOVEMENT);
        TaskHandle h = core.scheduler().submit(t);
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, h.state());
        core.permissions().applyServerPolicy(List.of("look"));
        assertEquals(TaskState.FAILED, h.state());
        assertEquals(List.of(StopReason.NOT_PERMITTED), t.stops);
    }

    @Test
    void explicitGrantAuthorizesAutomationAndRevocationStopsOnlyAffectedTasks() {
        core.permissions().setEnvironment(REMOTE);
        core.control().setMode(OperatingMode.AUTONOMOUS);
        core.permissions().applyServerPolicy(List.of("movement", "block_interaction"));
        Endless move = new Endless(Set.of(Capability.MOVEMENT), Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
        Endless mine = new Endless(Set.of(Capability.BLOCK_INTERACTION), Autonomy.AUTONOMOUS, TaskResource.INTERACTION);
        TaskHandle hm = core.scheduler().submit(move);
        TaskHandle hb = core.scheduler().submit(mine);
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, hm.state(), String.valueOf(hm.error()));
        assertEquals(TaskState.RUNNING, hb.state(), String.valueOf(hb.error()));
        core.permissions().applyServerPolicy(List.of("movement")); // block interaction revoked mid-task
        assertEquals(TaskState.FAILED, hb.state());
        assertEquals(List.of(StopReason.NOT_PERMITTED), mine.stops);
        assertNull(core.scheduler().holderOf(TaskResource.INTERACTION));
        assertEquals(TaskState.RUNNING, hm.state(), "losing one capability does not stop unrelated tasks");
    }

    @Test
    void disconnectWhileTasksAreScheduledStopsGameplayButKeepsClientLocalWork() {
        core.permissions().setEnvironment(REMOTE);
        core.control().setMode(OperatingMode.AUTONOMOUS);
        core.permissions().applyServerPolicy(List.of("movement"));
        core.world().enter("minecraft:overworld", 0);
        Endless running = new Endless(Set.of(Capability.MOVEMENT), Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
        TaskHandle hr = core.scheduler().submit(running);
        core.scheduler().tick();
        TaskHandle queued = core.scheduler().submit(new Endless(Set.of(Capability.MOVEMENT), Autonomy.AUTONOMOUS, TaskResource.MOVEMENT));
        TaskHandle plan = core.scheduler().submit(new Endless(Set.of(), Autonomy.AUTONOMOUS, TaskResource.CHAT));
        // As HafniumClient.handleDisconnect does it:
        core.world().clear();
        core.permissions().setEnvironment(Environment.NONE);
        assertEquals(TaskState.FAILED, hr.state());
        assertEquals(List.of(StopReason.NOT_PERMITTED), running.stops);
        core.scheduler().tick();
        assertEquals(TaskState.FAILED, queued.state(), "a queued gameplay task never starts after disconnect");
        assertEquals(0, queued.attempts());
        assertEquals(TaskState.RUNNING, plan.state());
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
        assertEquals(OperatingMode.MANUAL, core.control().mode());
    }

    @Test
    void reconnectingToAnotherServerStartsInManualWithoutTheOldGrant() {
        core.permissions().setEnvironment(REMOTE);
        core.permissions().applyServerPolicy(List.of("movement"));
        core.control().setMode(OperatingMode.AUTONOMOUS);
        core.permissions().setEnvironment(Environment.NONE);
        Environment other = new Environment(Environment.Kind.REMOTE, "other.example.net");
        core.profiles().put(new ServerProfile("other.example.net", true, Set.of()));
        core.permissions().setEnvironment(other, core.profiles().forServer(other.serverId()));
        assertEquals(OperatingMode.MANUAL, core.control().mode(), "the mode never carries over to another server");
        assertNull(core.permissions().serverPolicy());
        core.control().setMode(OperatingMode.AUTONOMOUS);
        TaskHandle auto = core.scheduler().submit(new Endless(Set.of(Capability.MOVEMENT), Autonomy.AUTONOMOUS, TaskResource.MOVEMENT));
        TaskHandle assist = core.scheduler().submit(new Endless(Set.of(Capability.LOOK), Autonomy.ASSISTED, TaskResource.LOOK));
        core.scheduler().tick();
        assertEquals(TaskState.FAILED, auto.state());
        assertTrue(auto.error().contains("other.example.net"), auto.error());
        assertEquals(TaskState.RUNNING, assist.state(), "this server's own profile allows assistance");
    }

    @Test
    void switchingModesStopsWhatTheNewModeDoesNotAllow() {
        core.permissions().setEnvironment(new Environment(Environment.Kind.INTEGRATED_HOST, "local:x"));
        assertEquals(OperatingMode.MANUAL, core.control().mode());
        Endless auto = new Endless(Set.of(Capability.MOVEMENT), Autonomy.AUTONOMOUS, TaskResource.MOVEMENT);
        Endless assist = new Endless(Set.of(Capability.LOOK), Autonomy.ASSISTED, TaskResource.LOOK);
        TaskHandle refused = core.scheduler().submit(new Endless(Set.of(Capability.LOOK), Autonomy.ASSISTED, TaskResource.LOOK));
        core.scheduler().tick();
        assertTrue(refused.error().contains("Manual mode"), refused.error());

        core.control().setMode(OperatingMode.AUTONOMOUS);
        TaskHandle ha = core.scheduler().submit(auto);
        TaskHandle hs = core.scheduler().submit(assist);
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, ha.state());
        assertEquals(TaskState.RUNNING, hs.state());

        core.control().setMode(OperatingMode.ASSIST); // no tick in between
        assertEquals(TaskState.FAILED, ha.state());
        assertTrue(ha.error().contains("needs Autonomous mode"), ha.error());
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
        assertEquals(TaskState.RUNNING, hs.state(), "assistance keeps running in Assist mode");

        core.control().setMode(OperatingMode.MANUAL);
        assertEquals(TaskState.FAILED, hs.state());
        assertEquals(List.of(StopReason.NOT_PERMITTED), assist.stops);
        assertNull(core.scheduler().holderOf(TaskResource.LOOK));
    }

    @Test
    void disconnectCancelsWorldTasksAndDropsPermission() {
        core.permissions().setEnvironment(new Environment(Environment.Kind.INTEGRATED_HOST, "local:x"));
        core.world().enter("minecraft:overworld", 0);
        TaskHandle world = core.scheduler().submit(new Endless(Set.of(), WorldScope.dimension("minecraft:overworld")));
        TaskHandle pure = core.scheduler().submit(new Endless(Set.of(), WorldScope.NONE),
                TaskOptions.builder().build());
        core.scheduler().tick();
        core.world().clear();
        assertEquals(TaskState.CANCELLED, world.state());
        assertFalse(pure.isDone(), "tasks that do not depend on the world survive");
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, pure.state(), "and take over the freed resource");
    }

    @Test
    void dimensionChangeAndChunkUnloadInvalidateScopedTasks() {
        core.world().enter("minecraft:overworld", 0);
        core.world().current().observeChunk(4, 4, null, List.of(), 0);
        Endless chunkTask = new Endless(Set.of(), WorldScope.chunks("minecraft:overworld", Set.of(Pos.chunkKey(4, 4))));
        TaskHandle c = core.scheduler().submit(chunkTask);
        core.scheduler().tick();
        core.world().current().unloadChunk(4, 4, 1);
        assertEquals(TaskState.FAILED, c.state());
        assertTrue(c.error().contains("Chunk 4,4 unloaded"));

        TaskHandle d = core.scheduler().submit(new Endless(Set.of(), WorldScope.dimension("minecraft:overworld")),
                TaskOptions.builder().build());
        core.scheduler().tick();
        core.world().enter("minecraft:the_nether", 2);
        assertEquals(TaskState.FAILED, d.state());
        assertTrue(d.error().contains("Left dimension minecraft:overworld"));
    }

    @Test
    void emergencyStopCancelsEverythingReleasesResourcesAndDropsToManual() {
        List<ControlEvent> events = new ArrayList<>();
        core.events().subscribe(ControlEvent.EmergencyStop.class, "t", events::add);
        core.control().setMode(OperatingMode.AUTONOMOUS);
        Endless a = new Endless(Set.of(), WorldScope.NONE);
        TaskHandle h1 = core.scheduler().submit(a);
        TaskHandle h2 = core.scheduler().submit(new Endless(Set.of(), WorldScope.NONE));
        TaskHandle h3 = core.scheduler().submit(new Endless(Set.of(), WorldScope.NONE), TaskOptions.builder().after(h1).build());
        core.scheduler().tick();
        core.control().emergencyStop("test");
        for (TaskHandle h : List.of(h1, h2, h3)) {
            assertEquals(TaskState.CANCELLED, h.state());
        }
        assertEquals(List.of(StopReason.CANCELLED), a.stops);
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
        assertEquals(OperatingMode.MANUAL, core.control().mode());
        assertEquals(1, events.size());
        core.scheduler().tick();
        TaskHandle after = core.scheduler().submit(new Endless(Set.of(), WorldScope.NONE));
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, after.state(), "the scheduler keeps working after an emergency stop");
    }

    @Test
    void modulesCannotStartTasksFromOnEnableAndTheirTasksStopWithThem() {
        List<TaskHandle> started = new ArrayList<>();
        Module m = core.modules().register(new Module("Runner", Category.AUTOMATION, "test") {
            ModuleContext ctx;

            @Override
            protected void onEnable(ModuleContext context) {
                ctx = context;
                started.add(context.submitTask(new Endless(Set.of(), WorldScope.NONE), TaskOptions.DEFAULT));
            }
        });
        core.modules().setEnabled(m, true);
        assertEquals(dev.hafnium.client.core.module.ModuleState.FAILED, m.state());
        assertTrue(m.lastError().contains("cannot be started from onEnable"));
        assertTrue(started.isEmpty());

        ModuleContext[] holder = new ModuleContext[1];
        Module ok = core.modules().register(new Module("Runner2", Category.AUTOMATION, "test") {
            @Override
            protected void onEnable(ModuleContext context) {
                holder[0] = context;
            }
        });
        core.modules().setEnabled(ok, true);
        TaskHandle h = holder[0].submitTask(new Endless(Set.of(), WorldScope.NONE), TaskOptions.DEFAULT); // "user action"
        core.scheduler().tick();
        assertEquals(TaskState.RUNNING, h.state());
        assertSame(ok, h.options().owner());
        core.modules().setEnabled(ok, false);
        assertEquals(TaskState.CANCELLED, h.state());
    }

    @Test
    void shutdownReleasesEverything() {
        TaskHandle h = core.scheduler().submit(new Endless(Set.of(), WorldScope.NONE));
        core.scheduler().tick();
        core.shutdown();
        assertEquals(TaskState.CANCELLED, h.state());
        assertNull(core.scheduler().holderOf(TaskResource.MOVEMENT));
        assertTrue(core.scheduler().isShutdown());
    }
}
