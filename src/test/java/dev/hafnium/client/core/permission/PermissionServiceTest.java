package dev.hafnium.client.core.permission;

import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.event.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.hafnium.client.core.permission.Autonomy.ASSISTED;
import static dev.hafnium.client.core.permission.Autonomy.AUTONOMOUS;
import static dev.hafnium.client.core.permission.Capability.*;
import static org.junit.jupiter.api.Assertions.*;

class PermissionServiceTest {
    static final Environment HOST = new Environment(Environment.Kind.INTEGRATED_HOST, "local:test");
    static final Environment REMOTE = new Environment(Environment.Kind.REMOTE, "play.example.net");
    static final Set<Capability> GAMEPLAY = EnumSet.of(MOVEMENT, LOOK, BLOCK_INTERACTION, INVENTORY, COMBAT_MOBS);

    OperatingMode mode;
    EventBus bus;
    PermissionService p;

    @BeforeEach
    void setUp() {
        mode = OperatingMode.AUTONOMOUS;
        bus = new EventBus();
        p = new PermissionService(bus, () -> mode);
    }

    ServerProfile profile(boolean assistance, Capability... prohibited) {
        return new ServerProfile(REMOTE.serverId(), assistance,
                prohibited.length == 0 ? Set.of() : EnumSet.of(prohibited[0], prohibited));
    }

    @Test
    void everyCapabilityHasTheIntendedTier() {
        for (Capability c : Capability.values()) {
            Capability.Tier expected = GAMEPLAY.contains(c) ? Capability.Tier.GAMEPLAY
                    : c == COMBAT_PLAYERS ? Capability.Tier.AUTHORIZED_ONLY : Capability.Tier.SERVER_PRIVILEGE;
            assertEquals(expected, c.tier(), c.name());
            assertSame(c, Capability.fromId(c.id()));
            assertFalse(c.displayName().isBlank());
        }
    }

    @Test
    void notInAWorldDeniesEveryCapability() {
        p.setPrivileges(new ServerPrivileges(true, true)); // ignored outside a world
        for (Capability c : Capability.values()) {
            assertFalse(p.decide(c, ASSISTED).allowed(), c.name());
            assertEquals("not in a world", p.decide(c, AUTONOMOUS).reason());
        }
    }

    // ---- 1. singleplayer (integrated host), no server authorization involved

    @Test
    void hostingAWorldAuthorizesAutomationButNotPrivilegesTheGameDidNotGive() {
        p.setEnvironment(HOST);
        for (Capability c : Capability.values()) {
            if (c.tier() != Capability.Tier.SERVER_PRIVILEGE) {
                assertTrue(p.decide(c, AUTONOMOUS).allowed(), c.name());
                assertTrue(p.decide(c, AUTONOMOUS).reason().contains("hosting"));
                assertTrue(p.decide(c, ASSISTED).allowed(), c.name());
            }
        }
        assertFalse(p.decide(OPERATOR_COMMANDS, AUTONOMOUS).allowed(), "a world without cheats has no operator");
        p.setPrivileges(new ServerPrivileges(true, false));
        assertTrue(p.decide(OPERATOR_COMMANDS, AUTONOMOUS).allowed());
        assertNull(p.profile(), "profiles apply to remote servers only");
    }

    // ---- 2. multiplayer without a Hafnium plugin

    @Test
    void remoteWithoutPolicyNeverAuthorizesAutonomousGameplay() {
        p.setEnvironment(REMOTE, profile(true));
        for (Capability c : Capability.values()) {
            Decision d = p.decide(c, AUTONOMOUS);
            assertFalse(d.allowed(), c.name());
        }
        Decision d = p.decide(MOVEMENT, AUTONOMOUS);
        assertTrue(d.reason().contains("requires authorization from play.example.net"), d.reason());
        assertTrue(d.reason().contains("normal gameplay packets are not treated as permission"), d.reason());
    }

    @Test
    void remoteAssistanceIsOffUntilTheProfileConfirmsTheRulesAllowIt() {
        p.setEnvironment(REMOTE); // no profile given: safe defaults
        assertNotNull(p.profile());
        assertFalse(p.profile().assistanceAllowed());
        Decision off = p.decide(MOVEMENT, ASSISTED);
        assertFalse(off.allowed());
        assertTrue(off.reason().contains("confirm in its Server Profile"), off.reason());

        p.updateProfile(profile(true));
        for (Capability c : GAMEPLAY) {
            Decision d = p.decide(c, ASSISTED);
            assertTrue(d.allowed(), c.name());
            assertTrue(d.reason().contains("rules and anticheat still apply"), d.reason());
        }
        Decision pvp = p.decide(COMBAT_PLAYERS, ASSISTED);
        assertFalse(pvp.allowed(), "player combat needs authorization even as assistance");
        assertTrue(pvp.reason().contains("combat_players requires authorization"), pvp.reason());
    }

    @Test
    void profileProhibitionsOnlyAffectTheProhibitedCapability() {
        p.setEnvironment(REMOTE, profile(true, INVENTORY));
        Decision inv = p.decide(INVENTORY, ASSISTED);
        assertFalse(inv.allowed());
        assertTrue(inv.reason().contains("marked prohibited on play.example.net"), inv.reason());
        assertTrue(p.decide(MOVEMENT, ASSISTED).allowed(), "one prohibited capability must not disable the rest");
        p.applyServerPolicy(List.of("inventory"));
        assertFalse(p.decide(INVENTORY, AUTONOMOUS).allowed(), "the user's prohibition wins over a server grant");
    }

    @Test
    void profileUpdatesForOtherServersAreIgnored() {
        p.setEnvironment(REMOTE, profile(false));
        p.updateProfile(new ServerProfile("other.example.net", true, Set.of()));
        assertFalse(p.profile().assistanceAllowed());
    }

    @Test
    void privilegedCapabilitiesFollowWhatTheServerAssigned() {
        p.setEnvironment(REMOTE, profile(true));
        assertFalse(p.decide(OPERATOR_COMMANDS, ASSISTED).allowed());
        assertTrue(p.decide(OPERATOR_COMMANDS, ASSISTED).reason().contains("operator permission"));
        p.setPrivileges(new ServerPrivileges(true, false));
        assertTrue(p.decide(OPERATOR_COMMANDS, ASSISTED).allowed());
        assertFalse(p.decide(OPERATOR_COMMANDS, AUTONOMOUS).allowed(), "autonomous use also needs authorization");
        assertFalse(p.decide(CREATIVE_INVENTORY, ASSISTED).allowed());
        p.setPrivileges(ServerPrivileges.NONE); // de-opped
        assertFalse(p.decide(OPERATOR_COMMANDS, ASSISTED).allowed());
    }

    // ---- 3. multiplayer with an explicit grant

    @Test
    void serverPolicyAuthorizesListedCapabilitiesAndSwitchesOffTheRest() {
        p.setEnvironment(REMOTE); // assistance not confirmed in the profile
        p.applyServerPolicy(List.of("movement", "combat_players", "not_a_capability"));
        assertEquals(Set.of(MOVEMENT, COMBAT_PLAYERS), p.serverPolicy().allowed());
        assertTrue(p.decide(MOVEMENT, AUTONOMOUS).allowed());
        assertTrue(p.decide(MOVEMENT, ASSISTED).allowed(), "a server grant also covers assistance");
        assertTrue(p.decide(COMBAT_PLAYERS, AUTONOMOUS).allowed());
        Decision look = p.decide(LOOK, ASSISTED);
        assertFalse(look.allowed());
        assertTrue(look.reason().contains("policy does not allow look"), look.reason());

        // 4. revocation
        p.applyServerPolicy(List.of());
        for (Capability c : Capability.values()) {
            assertFalse(p.decide(c, ASSISTED).allowed(), "an empty policy switches off everything: " + c);
        }
    }

    @Test
    void serverPolicyCannotGrantPrivileges() {
        p.setEnvironment(REMOTE);
        p.applyServerPolicy(List.of("operator_commands"));
        assertFalse(p.decide(OPERATOR_COMMANDS, AUTONOMOUS).allowed());
        p.setPrivileges(new ServerPrivileges(true, false));
        assertTrue(p.decide(OPERATOR_COMMANDS, AUTONOMOUS).allowed());
    }

    // ---- 5/6. disconnect and reconnecting elsewhere

    @Test
    void policyAndPrivilegesAreDroppedWhenTheEnvironmentChanges() {
        p.setEnvironment(REMOTE, profile(true));
        p.applyServerPolicy(List.of("combat_players"));
        p.setPrivileges(new ServerPrivileges(true, true));
        Environment other = new Environment(Environment.Kind.REMOTE, "other.example.net");
        p.setEnvironment(other, new ServerProfile("other.example.net", false, Set.of()));
        assertNull(p.serverPolicy());
        assertEquals(ServerPrivileges.NONE, p.privileges());
        assertEquals("other.example.net", p.profile().serverId());
        assertFalse(p.decide(COMBAT_PLAYERS, AUTONOMOUS).allowed());
        assertFalse(p.decide(MOVEMENT, ASSISTED).allowed(), "the other server's profile applies");
        p.setEnvironment(Environment.NONE);
        assertNull(p.profile());
        assertNull(p.applyServerPolicy(List.of("movement")), "policies outside a world are ignored");
    }

    // ---- 7. operating modes

    @Test
    void operatingModeGatesEverything() {
        p.setEnvironment(HOST);
        mode = OperatingMode.MANUAL;
        Decision manual = p.decide(MOVEMENT, ASSISTED);
        assertFalse(manual.allowed());
        assertTrue(manual.reason().startsWith("Manual mode"), manual.reason());
        mode = OperatingMode.ASSIST;
        assertTrue(p.decide(MOVEMENT, ASSISTED).allowed());
        Decision auto = p.decide(MOVEMENT, AUTONOMOUS);
        assertFalse(auto.allowed());
        assertTrue(auto.reason().contains("needs Autonomous mode"), auto.reason());
        mode = OperatingMode.AUTONOMOUS;
        assertTrue(p.decide(MOVEMENT, AUTONOMOUS).allowed());
        assertTrue(p.decide(MOVEMENT, ASSISTED).allowed());
    }

    @Test
    void statusIgnoresTheModeAndClassifiesEachCapability() {
        mode = OperatingMode.MANUAL;
        p.setEnvironment(REMOTE, profile(true, BLOCK_INTERACTION));
        var s = p.statuses();
        assertEquals(Capability.values().length, s.size());
        assertEquals(CapabilityStatus.Level.ASSIST_ONLY, s.get(MOVEMENT).level());
        assertEquals(CapabilityStatus.Level.UNAVAILABLE, s.get(BLOCK_INTERACTION).level());
        assertEquals(CapabilityStatus.Level.UNAVAILABLE, s.get(COMBAT_PLAYERS).level());
        assertTrue(s.get(MOVEMENT).reason().contains("requires authorization"), s.get(MOVEMENT).reason());
        p.applyServerPolicy(List.of("movement"));
        assertEquals(CapabilityStatus.Level.AUTONOMOUS, p.status(MOVEMENT).level());
    }

    @Test
    void clientSettingsCanOnlyRestrict() {
        p.setEnvironment(HOST);
        p.setUserRestrictions(Set.of(COMBAT_PLAYERS));
        assertFalse(p.decide(COMBAT_PLAYERS, ASSISTED).allowed());
        assertTrue(p.decide(MOVEMENT, AUTONOMOUS).allowed());
        p.setUserRestrictions(Set.of());

        // Even the most permissive profile grants nothing autonomous on a remote server.
        p.setEnvironment(REMOTE, profile(true));
        for (Capability c : Capability.values()) {
            assertFalse(p.decide(c, AUTONOMOUS).allowed(), "no client-side setting authorizes automation: " + c);
        }
    }

    @Test
    void changesPostEventsOnlyWhenSomethingDiffers() {
        List<PermissionChangedEvent> events = new ArrayList<>();
        bus.subscribe(PermissionChangedEvent.class, "t", events::add);
        p.setEnvironment(REMOTE, profile(false));
        assertEquals(1, events.size());
        assertTrue(events.get(0).environmentChanged());
        p.setPrivileges(ServerPrivileges.NONE);
        p.setEnvironment(REMOTE, profile(false));
        assertEquals(1, events.size(), "unchanged state must not trigger re-checks every tick");
        p.setPrivileges(new ServerPrivileges(true, false));
        p.updateProfile(profile(true));
        assertEquals(3, events.size());
        assertFalse(events.get(1).environmentChanged());
        assertTrue(events.get(2).profile().assistanceAllowed());
    }

    @Test
    void decideAllReportsFirstDenial() {
        p.setEnvironment(REMOTE, profile(true));
        Decision d = p.decideAll(Set.of(MOVEMENT, COMBAT_PLAYERS), ASSISTED);
        assertFalse(d.allowed());
        assertTrue(d.reason().contains("combat_players"));
        assertTrue(p.decideAll(GAMEPLAY, ASSISTED).allowed());
    }
}
