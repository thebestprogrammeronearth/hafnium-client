package dev.hafnium.client.modules.client;
import com.mojang.blaze3d.platform.NativeImage;
import dev.hafnium.client.core.ImmutableSnapshot;
import dev.hafnium.client.core.event.*;
import dev.hafnium.client.core.module.*;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.*;
import dev.hafnium.client.core.task.*;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.*;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.spotify.SpotifySession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
public final class SpotifyHudModule extends Module {
    private record Request(String action) implements ImmutableSnapshot {}
    public final NumberSetting x = setting(new NumberSetting("X", 4000, 0, 4000, 1));
    public final NumberSetting y = setting(new NumberSetting("Y", 4, 0, 4000, 1));
    public final NumberSetting scale = setting(new NumberSetting("Scale", 1, .5, 3, .1));
    public final NumberSetting opacity = setting(new NumberSetting("Opacity", 90, 10, 100, 1, "%"));
    public final BoolSetting compact = setting(new BoolSetting("Compact", false));
    public final BoolSetting art = setting(new BoolSetting("Album art", true));
    public final BoolSetting progress = setting(new BoolSetting("Progress", true));
    public final BoolSetting hide = setting(new BoolSetting("Auto hide", true));
    public final BoolSetting controls = setting(new BoolSetting("Playback controls", false));
    public final ColorSetting accent = setting(new ColorSetting("Accent", 0xFFB77CFF));
    public final KeybindSetting edit = setting(new KeybindSetting("Drag HUD"));
    public final KeybindSetting toggle = setting(new KeybindSetting("Play or pause"));
    private final SpotifySession session = new SpotifySession();
    private TaskHandle pending;
    private long nextPoll, received;
    private SpotifySession.Playback playback;
    private String track = "", error = "Waiting for Spotify", artwork = "";
    private float fade;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("hafnium", "spotify-art");
    private boolean texture;
    public SpotifyHudModule() {
        super("SpotifyHUD", Category.CLIENT,
                "Spotify via Linux MPRIS. Requires Python 3 + PyGObject. Bind Drag HUD to reposition.");
    }
    protected void onEnable(ModuleContext ctx) {
        nextPoll = 0;
        ctx.subscribe(ClientTickEvent.class, e -> {
            if (e == ClientTickEvent.END) {
                long now = System.currentTimeMillis();
                if (now >= nextPoll && (pending == null || pending.isDone())) {
                    nextPoll = now + 1000;
                    request(ctx, "poll");
                }
            }
        });
        ctx.subscribe(GameKeyEvent.class, e -> {
            if (e.key() == edit.get())
                Minecraft.getInstance().setScreen(new dev.hafnium.client.gui.utility.SpotifyEditScreen(this));
            else if (e.key() == toggle.get() && controls.get() && playback != null && playback.controls())
                request(ctx, "toggle");
        });
        ctx.subscribe(HudRenderEvent.class, e -> {
            if (!Minecraft.getInstance().options.hideGui)
                draw(e.graphics(), false);
        });
    }
    private void request(ModuleContext ctx, String action) {
        if (pending != null && !pending.isDone())
            return;
        pending = ctx.submitTask(new Task() {
            AsyncResult<SpotifySession.Result> result;
            public String name() {
                return "Spotify media session";
            }
            public void start(TaskContext c) {
                result = c.async(new Request(action), r -> session.query(r.action));
            }
            public boolean tick(TaskContext c) {
                if (!result.isDone())
                    return false;
                accept(result.get());
                return true;
            }
        }, TaskOptions.DEFAULT);
    }
    private void accept(SpotifySession.Result result) {
        playback = result.playback();
        received = System.currentTimeMillis();
        error = playback.error() == null ? "" : friendly(playback.error());
        if (!error.isEmpty()) {
            playback = null;
            releaseTexture();
            artwork = "";
            return;
        }
        if (!java.util.Objects.equals(track, playback.track())) {
            track = playback.track();
            fade = 0;
        }
        if (!java.util.Objects.equals(artwork, playback.art())) {
            artwork = playback.art();
            releaseTexture();
            if (result.pixels() != null) {
                NativeImage image = new NativeImage(result.width(), result.height(), true);
                for (int yy = 0; yy < result.height(); yy++)
                    for (int xx = 0; xx < result.width(); xx++)
                        image.setPixel(xx, yy, result.pixels()[yy * result.width() + xx]);
                Minecraft.getInstance().getTextureManager().register(
                        TEXTURE, new DynamicTexture(() -> "Spotify artwork", image));
                texture = true;
            }
        }
    }
    public void draw(net.minecraft.client.gui.GuiGraphics g, boolean editing) {
        var mc = Minecraft.getInstance();
        boolean available = playback != null && !playback.title().isBlank() && !playback.stopped();
        if (!editing && hide.get() && !available)
            return;
        fade = Math.min(1, fade + .05f);
        float s = scale.get().floatValue();
        float px = Math.min(x.get().floatValue(), Math.max(0, g.guiWidth() - 240 * s - 4)),
              py = Math.min(y.get().floatValue(), Math.max(0, g.guiHeight() - 70 * s));
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(s, s);
        ThemeManager.sync();
        int guiScale = Math.max(1, mc.getWindow().getGuiScale());
        GuiRenderUtil.begin(g, guiScale, guiScale);
        try {
            float h = compact.get() ? 42 : 70;
            GuiRenderUtil.roundRect(
                    0, 0, 240, h, 6, ThemeManager.withAlpha(ThemeManager.BACKGROUND, opacity.get().floatValue() / 100));
            GuiRenderUtil.roundOutline(0, 0, 240, h, 6, accent.get());
            float tx = 8;
            if (art.get()) {
                if (texture)
                    g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 6 * guiScale, 6 * guiScale, 0, 0, 30 * guiScale,
                            30 * guiScale, 96, 96, 96, 96);
                else
                    GuiRenderUtil.text(FontRole.TITLE, "♪", 12, 20, accent.get());
                tx = 42;
            }
            String title = available ? playback.title() : "Spotify unavailable",
                   artist = available ? playback.artist() : error;
            GuiRenderUtil.pushScissor(tx, 3, 230 - tx, 34);
            float width = GuiRenderUtil.textWidth(FontRole.TEXT, title);
            float scroll = width > 225 - tx
                    ? (float) ((Math.sin(System.currentTimeMillis() / 3000d) + 1) / 2 * (width - (225 - tx)))
                    : 0;
            GuiRenderUtil.text(FontRole.TEXT, title, tx - scroll, 14,
                    ThemeManager.withAlpha(ThemeManager.TEXT, available ? fade : 1));
            GuiRenderUtil.text(FontRole.SMALL, artist, tx, 28, ThemeManager.TEXT_SECONDARY);
            GuiRenderUtil.popScissor();
            if (!compact.get() && available) {
                long pos = Math.min(playback.duration(),
                        playback.position() + (playback.playing() ? System.currentTimeMillis() - received : 0));
                if (progress.get()) {
                    GuiRenderUtil.roundRect(8, 45, 224, 3, 1, ThemeManager.TRACK);
                    GuiRenderUtil.roundRect(
                            8, 45, playback.duration() > 0 ? 224f * pos / playback.duration() : 0, 3, 1, accent.get());
                }
                GuiRenderUtil.text(FontRole.SMALL,
                        time(pos) + " / " + time(playback.duration()) + " · "
                                + (playback.playing() ? "Playing" : "Paused"),
                        8, 58, ThemeManager.TEXT_SECONDARY);
            }
        } finally {
            GuiRenderUtil.end();
            g.pose().popMatrix();
        }
    }
    /** Short explanation for common backend failures; anything else is shown as reported. */
    static String friendly(String error) {
        if (error.contains("ServiceUnknown") || error.contains("NameHasNoOwner"))
            return "Spotify is not running";
        if (error.contains("No module named") && error.contains("gi"))
            return "Install PyGObject (python-gobject) for Spotify support";
        if (error.contains("Cannot run program \"python3\""))
            return "Python 3 is required for Spotify support";
        return error;
    }
    private static String time(long ms) {
        return (ms / 60000) + ":" + String.format(java.util.Locale.ROOT, "%02d", ms / 1000 % 60);
    }
    private void releaseTexture() {
        if (texture) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = false;
        }
    }
    protected void onDisable() {
        releaseTexture();
        artwork = "";
        playback = null;
    }
}
