package dev.hafnium.client.core.permission;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskContext;
import dev.hafnium.client.core.world.WorldModelLimits;
import dev.hafnium.client.modules.automation.OperatingModeModule;
import dev.hafnium.client.modules.client.ServerProfileModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** User-facing warnings, and the GUI modules that edit the mode and server profiles. */
class PermissionNoticesTest {
    static final Environment REMOTE = new Environment(Environment.Kind.REMOTE, "play.example.net");

    HafniumCore core;
    List<PermissionNotices.Notice> shown;

    @BeforeEach
    void setUp() {
        core = new HafniumCore(ClientThread.DIRECT, WorldModelLimits.DEFAULT);
        shown = new ArrayList<>();
        core.notices().setSink(shown::add);
    }

    @AfterEach
    void tearDown() {
        core.shutdown();
    }

    String all() {
        StringBuilder sb = new StringBuilder();
        for (PermissionNotices.Notice n : shown) {
            sb.append(n.level()).append(": ").append(n.text()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void joiningAServerWithoutPluginExplainsWhatIsUnavailable() {
        core.permissions().setEnvironment(REMOTE, ServerProfile.DEFAULTS.withServerId(REMOTE.serverId()));
        PermissionNotices.Notice n = shown.get(0);
        assertEquals(PermissionNotices.Level.WARNING, n.level());
        assertTrue(n.text().contains("autonomous automation and player combat are unavailable"), n.text());
        assertTrue(n.text().contains("assistance is off until you confirm"), n.text());
        assertTrue(n.text().contains("Client-side features work normally"), n.text());
        assertTrue(n.text().contains("cannot prevent penalties"), n.text());
    }

    @Test
    void grantsRevocationsAndDenialsAreReported() {
        core.permissions().setEnvironment(REMOTE);
        core.control().setMode(OperatingMode.AUTONOMOUS);
        assertTrue(all().contains("has not authorized automation, so nothing can run autonomously here"), all());

        core.scheduler().submit(new Task() {
            @Override
            public String name() {
                return "Mine Ore";
            }

            @Override
            public Set<Capability> capabilities() {
                return Set.of(Capability.BLOCK_INTERACTION);
            }

            @Override
            public boolean tick(TaskContext ctx) {
                return false;
            }
        });
        core.scheduler().tick();
        assertTrue(all().contains("ERROR: Mine Ore unavailable: autonomous block_interaction requires authorization"), all());

        core.permissions().applyServerPolicy(List.of("movement"));
        assertTrue(all().contains("play.example.net authorized: movement"), all());
        core.permissions().applyServerPolicy(List.of());
        assertTrue(all().contains("revoked all Hafnium automation"), all());
    }

    @Test
    void identicalNoticesAreNotRepeatedInABurst() {
        core.permissions().setEnvironment(REMOTE);
        for (int i = 0; i < 5; i++) {
            core.scheduler().submit(new Task() {
                @Override
                public String name() {
                    return "Walk";
                }

                @Override
                public Set<Capability> capabilities() {
                    return Set.of(Capability.MOVEMENT);
                }

                @Override
                public boolean tick(TaskContext ctx) {
                    return false;
                }
            });
        }
        core.scheduler().tick();
        long walk = shown.stream().filter(n -> n.text().startsWith("Walk unavailable")).count();
        assertEquals(1, walk);
    }

    @Test
    void operatingModeModuleStaysInSyncBothWays() {
        OperatingModeModule m = core.modules().register(new OperatingModeModule());
        m.bind(core.events(), core.control());
        assertEquals("Manual", m.mode.get());
        m.mode.set("Assist");
        assertEquals(OperatingMode.ASSIST, core.control().mode());
        core.control().emergencyStop("test");
        assertEquals("Manual", m.mode.get(), "the setting follows emergency stops");
        m.mode.set("Autonomous");
        core.permissions().setEnvironment(REMOTE);
        assertEquals("Manual", m.mode.get(), "joining a server resets the mode");
    }

    @Test
    void serverProfileModuleEditsTheCurrentServerOrTheDefaults() {
        ServerProfileModule m = core.modules().register(new ServerProfileModule());
        m.bind(core.events(), core.profiles(), core.permissions());

        m.prohibitSetting(Capability.INVENTORY).set(true); // not on a server: edits the defaults
        assertTrue(core.profiles().defaults().prohibits(Capability.INVENTORY));

        core.permissions().setEnvironment(REMOTE, core.profiles().forServer(REMOTE.serverId()));
        assertTrue(m.prohibitSetting(Capability.INVENTORY).get(), "new servers start from the defaults");
        assertFalse(m.assistanceAllowed.get());
        core.control().setMode(OperatingMode.ASSIST);
        assertFalse(core.permissions().decide(Capability.MOVEMENT, Autonomy.ASSISTED).allowed());

        m.assistanceAllowed.set(true);
        assertTrue(core.profiles().isConfigured(REMOTE.serverId()));
        assertTrue(core.permissions().profile().assistanceAllowed(), "edits apply to the live session immediately");
        assertTrue(core.permissions().decide(Capability.MOVEMENT, Autonomy.ASSISTED).allowed());
        assertFalse(core.permissions().decide(Capability.MOVEMENT, Autonomy.AUTONOMOUS).allowed(),
                "the profile never authorizes automation");
        assertFalse(core.profiles().defaults().assistanceAllowed(), "server edits do not change the defaults");

        core.permissions().setEnvironment(Environment.NONE);
        assertFalse(m.assistanceAllowed.get(), "back to showing the defaults");
    }
}
