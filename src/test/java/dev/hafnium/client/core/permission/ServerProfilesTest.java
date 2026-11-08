package dev.hafnium.client.core.permission;

import dev.hafnium.client.config.ServerProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ServerProfilesTest {
    @Test
    void addressesAreNormalized() {
        assertEquals("play.example.net", ServerProfile.normalize(" Play.Example.NET:25565 "));
        assertEquals("play.example.net:25566", ServerProfile.normalize("play.example.net:25566"));
        assertEquals("play.example.net", ServerProfile.normalize("play.example.net."));
        assertEquals(ServerProfile.DEFAULT_ID, ServerProfile.normalize(""));
    }

    @Test
    void unknownServersGetACopyOfTheSafeDefaults() {
        ServerProfiles profiles = new ServerProfiles();
        ServerProfile p = profiles.forServer("new.example.net");
        assertEquals("new.example.net", p.serverId());
        assertFalse(p.assistanceAllowed(), "assistance stays off until the user confirms the rules allow it");
        assertTrue(p.prohibited().isEmpty());
        assertFalse(profiles.isConfigured("new.example.net"));

        profiles.put(new ServerProfile(ServerProfile.DEFAULT_ID, false, Set.of(Capability.INVENTORY)));
        assertTrue(profiles.forServer("other.example.net").prohibits(Capability.INVENTORY));
        assertTrue(profiles.all().isEmpty(), "editing the defaults does not create server entries");
    }

    @Test
    void perServerProfilesAreIndependent() {
        ServerProfiles profiles = new ServerProfiles();
        int[] changes = {0};
        profiles.onChange(x -> changes[0]++);
        profiles.put(new ServerProfile("A.example.net:25565", true, Set.of()));
        profiles.put(profiles.forServer("b.example.net").withProhibited(Capability.MOVEMENT, true));
        assertTrue(profiles.forServer("a.example.net").assistanceAllowed());
        assertFalse(profiles.forServer("b.example.net").assistanceAllowed());
        assertTrue(profiles.forServer("b.example.net").prohibits(Capability.MOVEMENT));
        assertFalse(profiles.forServer("a.example.net").prohibits(Capability.MOVEMENT));
        assertEquals(2, changes[0]);
        profiles.remove("a.example.net");
        assertFalse(profiles.isConfigured("a.example.net"));
    }

    @Test
    void storeRoundTripsAndFallsBackToDefaultsOnBadFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("hafnium/server-profiles.json");
        ServerProfiles profiles = new ServerProfiles();
        new ServerProfileStore(file).attach(profiles);
        profiles.put(new ServerProfile("a.example.net", true, Set.of(Capability.COMBAT_MOBS, Capability.INVENTORY)));
        profiles.put(new ServerProfile(ServerProfile.DEFAULT_ID, false, Set.of(Capability.LOOK)));
        assertTrue(Files.isRegularFile(file));

        ServerProfiles loaded = new ServerProfiles();
        new ServerProfileStore(file).load(loaded);
        assertEquals(profiles.forServer("a.example.net"), loaded.forServer("a.example.net"));
        assertTrue(loaded.defaults().prohibits(Capability.LOOK));
        assertEquals(List.copyOf(profiles.all()), List.copyOf(loaded.all()));

        Files.writeString(file, "{ not json");
        ServerProfiles broken = new ServerProfiles();
        new ServerProfileStore(file).load(broken);
        assertEquals(ServerProfile.DEFAULTS, broken.defaults());
        assertTrue(broken.all().isEmpty());
    }
}
