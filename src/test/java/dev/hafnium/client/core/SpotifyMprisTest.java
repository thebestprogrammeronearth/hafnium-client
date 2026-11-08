package dev.hafnium.client.core;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
class SpotifyMprisTest {
    @Test
    void supportedMediaSessionProtocolOnPrivateBus() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux"));
        var p = new ProcessBuilder("dbus-run-session", "--", "python3", "src/test/resources/mpris_fixture.py",
                "src/main/resources/assets/hafnium/spotify_mpris.py")
                        .redirectErrorStream(true)
                        .start();
        boolean finished = p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished)
            p.destroyForcibly();
        assertTrue(finished, "MPRIS fixture timed out");
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), output);
    }
}
