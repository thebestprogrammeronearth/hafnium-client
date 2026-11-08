package dev.hafnium.client.spotify;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
/** Linux MPRIS adapter. Called exclusively on scheduler workers; no application-internal scraping. */
public final class SpotifySession {
    public record Playback(String title, String artist, String track, String art, long position, long duration,
            boolean playing, boolean stopped, boolean controls, String error) {}
    public record Result(Playback playback, int[] pixels, int width, int height) {}
    private String artwork = "";
    private int[] pixels;
    private int width, height;
    public synchronized Result query(String action) {
        try {
            if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux"))
                throw new IOException("MPRIS backend requires Linux; no supported backend configured for this OS");
            String script;
            try (var stream = SpotifySession.class.getResourceAsStream("/assets/hafnium/spotify_mpris.py")) {
                if (stream == null)
                    throw new IOException("Missing MPRIS bridge");
                script = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            Process process = new ProcessBuilder("python3", "-c", script, action).redirectErrorStream(true).start();
            String text;
            try {
                if (!process.waitFor(4, TimeUnit.SECONDS))
                    throw new IOException("MPRIS request timed out");
                text = new String(process.getInputStream().readNBytes(65536), java.nio.charset.StandardCharsets.UTF_8);
                if (process.exitValue() != 0)
                    throw new IOException("Python 3 with PyGObject/Gio is required: "
                            + text.substring(0, Math.min(text.length(), 200)));
            } finally {
                process.destroyForcibly();
            }
            Playback p = new Gson().fromJson(text, Playback.class);
            if (p == null)
                throw new IOException("Empty media-session response");
            if (p.error != null && !p.error.isEmpty())
                return new Result(p, null, 0, 0);
            if (!java.util.Objects.equals(artwork, p.art)) {
                artwork = p.art;
                pixels = null;
                try {
                    loadArt(artwork);
                } catch (Exception ignored) {
                    width = height = 0;
                }
            }
            return new Result(p, pixels, width, height);
        } catch (Exception e) {
            return new Result(new Playback("", "", "", "", 0, 0, false, true, false, e.getMessage()), null, 0, 0);
        }
    }
    private void loadArt(String url) throws Exception {
        if (url == null || url.isBlank())
            return;
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme())
                || !("i.scdn.co".equals(uri.getHost()) || "image-cdn-ak.spotifycdn.com".equals(uri.getHost())))
            return;
        var connection = (javax.net.ssl.HttpsURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(3000);
        connection.setInstanceFollowRedirects(false);
        try (var body = connection.getInputStream()) {
            if (connection.getResponseCode() != 200)
                return;
            byte[] bytes = body.readNBytes(2_000_001);
            if (bytes.length > 2_000_000)
                return;
            java.awt.image.BufferedImage image;
            try (var imageInput = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext())
                    return;
                var reader = readers.next();
                try {
                    reader.setInput(imageInput);
                    if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096)
                        return;
                    image = reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
            if (image == null)
                return;
            var small = new java.awt.image.BufferedImage(96, 96, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = small.createGraphics();
            try {
                g.drawImage(image, 0, 0, 96, 96, null);
            } finally {
                g.dispose();
            }
            width = height = 96;
            pixels = small.getRGB(0, 0, 96, 96, null, 0, 96);
        } finally {
            connection.disconnect();
        }
    }
}
