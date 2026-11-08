package dev.hafnium.client.modules.render;

import dev.hafnium.client.anubis.chat.ModuleChat;
import dev.hafnium.client.anubis.esp.BlockLayer;
import dev.hafnium.client.anubis.esp.EspStyle;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.setting.StringSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.event.WorldRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.LayoutElement;
import dev.hafnium.client.schematic.Schematic;
import dev.hafnium.client.schematic.SchematicLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Schematic Preview: a 3D building guide. Put .nbt (structure blocks, Create), .schem (WorldEdit / Sponge) or
 * .litematic files in config/hafnium/schematics, type the file name, press "Place Here". Missing blocks are outlined
 * in one colour, wrong blocks in another, and a panel shows progress and the materials still needed. Purely visual:
 * nothing is placed for you.
 */
public final class SchematicPreviewModule extends Module {
    private enum Check { MISSING, WRONG, CORRECT, UNLOADED }

    private static final int CHECKS_PER_TICK = 40_000;

    private final StringSetting file = setting(new StringSetting("File", ""));
    private final ActionSetting load = setting(new ActionSetting("Load", this::load));
    private final ActionSetting placeHere = setting(new ActionSetting("Place Here", this::placeHere));
    private final ActionSetting openFolder = setting(new ActionSetting("Open Folder", this::openFolder));
    private final ModeSetting rotation = setting(new ModeSetting("Rotation", "0°", "0°", "90°", "180°", "270°"));
    private final NumberSetting offsetX = setting(new NumberSetting("Offset X", 0, -64, 64, 1));
    private final NumberSetting offsetY = setting(new NumberSetting("Offset Y", 0, -64, 64, 1));
    private final NumberSetting offsetZ = setting(new NumberSetting("Offset Z", 0, -64, 64, 1));
    private final ModeSetting layers = setting(new ModeSetting("Layers", "All", "All", "Up To Layer", "Single Layer"));
    private final NumberSetting layer = setting(new NumberSetting("Layer", 1, 1, 384, 1));
    private final BoolSetting showWrong = setting(new BoolSetting("Show Wrong Blocks", true));
    private final ColorSetting missingColor = setting(new ColorSetting("Missing Color", 0xFF5BC8FF));
    private final ColorSetting wrongColor = setting(new ColorSetting("Wrong Color", 0xFFFF6B81));
    private final EspStyle style = new EspStyle(this::setting, EspStyle.BOXES, 0.18F, 256.0F);
    private final LayoutElement hud = new LayoutElement("schematic", "Schematic Preview", () -> isEnabled() && this.schematic != null, 0f, 1f, 4f, -40f);

    private Schematic schematic;
    private BlockPos origin;
    private Check[] checks = new Check[0];
    private int cursor;
    private int missing, wrong, correct, unloaded;
    private final Map<Item, Integer> materials = new HashMap<>();
    private List<Map.Entry<Item, Integer>> topMaterials = List.of();
    private final BlockLayer layerRender = new BlockLayer(new BlockLayer.Palette() {
        @Override
        public int color(Object kind) {
            return kind == Check.WRONG ? wrongColor.argb() : missingColor.argb();
        }

        @Override
        public int tracerColor(Object kind) {
            return 0;
        }
    }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());

    public SchematicPreviewModule() {
        super("Schematic Preview", Category.RENDER,
                "3D building guide from .nbt, .schem or .litematic files in config/hafnium/schematics: missing and wrong blocks, progress, materials.");
        Runnable restart = this::restart;
        rotation.onChange(restart);
        offsetX.onChange(restart);
        offsetY.onChange(restart);
        offsetZ.onChange(restart);
        layers.onChange(restart);
        layer.onChange(restart);
        showWrong.onChange(restart);
        layer.visibleWhen(() -> !layers.is("All"));
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("hafnium").resolve("schematics");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        ctx.subscribe(ClientTickEvent.class, e -> {
            if (e == ClientTickEvent.END) {
                tick();
            }
        });
        ctx.subscribe(WorldRenderEvent.class, e -> layerRender.render(e, style));
        ctx.subscribe(HudRenderEvent.class, this::renderHud);
        if (schematic == null && !file.get().isBlank()) {
            load();
        }
    }

    @Override
    protected void onDisable() {
        layerRender.clear();
        checks = new Check[checks.length];
        cursor = 0;
    }

    // ------------------------------------------------------------------ actions

    private void say(ModuleChat.Body body) {
        ModuleChat.send(name(), body);
    }

    /** Loads the file named in "File" (with or without extension) from the schematics folder. */
    public void load() {
        String wanted = file.get().trim();
        if (wanted.isEmpty()) {
            say(ModuleChat.body().warn("Type a file name in File first").text(" (Open Folder shows where they go)."));
            return;
        }
        try {
            Files.createDirectories(folder());
            Path path = resolve(wanted);
            if (path == null) {
                say(ModuleChat.body().danger("Not found: ").name(wanted).text(" in config/hafnium/schematics"));
                return;
            }
            schematic = SchematicLoader.load(path);
            checks = new Check[schematic.blocks()];
            cursor = 0;
            layerRender.clear();
            say(ModuleChat.body().good("Loaded ").name(schematic.name()).text(" · ").value(schematic.blocks()).text(" blocks, ")
                    .value(schematic.sizeX() + "×" + schematic.sizeY() + "×" + schematic.sizeZ())
                    .text(origin == null ? ". Press Place Here." : "."));
        } catch (IOException | RuntimeException e) {
            schematic = null;
            say(ModuleChat.body().danger("Could not read ").name(wanted).text(": " + e.getMessage()));
        }
    }

    private static Path resolve(String wanted) throws IOException {
        Path direct = folder().resolve(wanted).normalize();
        if (!direct.startsWith(folder())) {
            return null;
        }
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        for (String ext : new String[]{".litematic", ".schem", ".schematic", ".nbt"}) {
            Path p = folder().resolve(wanted + ext);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        try (Stream<Path> files = Files.list(folder())) {
            return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(wanted.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
    }

    /** Anchors the schematic's minimum corner at the block you stand on. */
    public void placeHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        origin = mc.player.blockPosition();
        offsetX.reset();
        offsetY.reset();
        offsetZ.reset();
        restart();
        if (schematic == null) {
            load();
        } else {
            say(ModuleChat.body().text("Placed at ").coords(origin.getX(), origin.getY(), origin.getZ()));
        }
    }

    private void openFolder() {
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
        }
        Util.getPlatform().openPath(folder());
    }

    private void restart() {
        if (checks.length > 0) {
            checks = new Check[checks.length];
        }
        cursor = 0;
        layerRender.clear();
    }

    // ------------------------------------------------------------------ comparison

    private Rotation rot() {
        return switch (rotation.get()) {
            case "90°" -> Rotation.CLOCKWISE_90;
            case "180°" -> Rotation.CLOCKWISE_180;
            case "270°" -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** World position of schematic block i (rotation around the origin corner). */
    public BlockPos worldPos(int i) {
        int x = schematic.xs()[i], z = schematic.zs()[i];
        int rx, rz;
        switch (rot()) {
            case CLOCKWISE_90 -> {
                rx = -z;
                rz = x;
            }
            case CLOCKWISE_180 -> {
                rx = -x;
                rz = -z;
            }
            case COUNTERCLOCKWISE_90 -> {
                rx = z;
                rz = -x;
            }
            default -> {
                rx = x;
                rz = z;
            }
        }
        return origin.offset(rx + offsetX.getInt(), schematic.ys()[i] + offsetY.getInt(), rz + offsetZ.getInt());
    }

    private boolean shownLayer(int y) {
        int l = layer.getInt() - 1;
        return switch (layers.get()) {
            case "Up To Layer" -> y <= l;
            case "Single Layer" -> y == l;
            default -> true;
        };
    }

    private void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (schematic == null || origin == null || level == null) {
            return;
        }
        int n = schematic.blocks();
        Rotation rot = rot();
        int budget = Math.min(n, CHECKS_PER_TICK);
        for (int k = 0; k < budget; k++) {
            int i = cursor;
            cursor = (cursor + 1) % n;
            Check next;
            BlockPos pos = worldPos(i);
            if (!shownLayer(schematic.ys()[i])) {
                next = null;
            } else if (!level.isLoaded(pos)) {
                next = Check.UNLOADED;
            } else {
                BlockState want = schematic.states()[i].rotate(rot);
                BlockState have = level.getBlockState(pos);
                if (have.equals(want)) {
                    next = Check.CORRECT;
                } else if (have.isAir() || have.canBeReplaced() && !want.is(have.getBlock())) {
                    next = Check.MISSING;
                } else {
                    next = have.is(want.getBlock()) ? Check.CORRECT : Check.WRONG;
                }
            }
            if (next != checks[i]) {
                long key = pos.asLong();
                if (checks[i] == Check.MISSING || checks[i] == Check.WRONG) {
                    layerRender.remove(key);
                }
                if (next == Check.MISSING || next == Check.WRONG && showWrong.get()) {
                    layerRender.put(key, next);
                }
                checks[i] = next;
            }
            if (cursor == 0) {
                recount();
            }
        }
        if (n > CHECKS_PER_TICK && cursor % 20_000 < budget) {
            recount();
        }
    }

    private void recount() {
        missing = wrong = correct = unloaded = 0;
        materials.clear();
        for (int i = 0; i < checks.length; i++) {
            Check c = checks[i];
            if (c == null) {
                continue;
            }
            switch (c) {
                case MISSING -> {
                    missing++;
                    Block block = schematic.states()[i].getBlock();
                    Item item = block.asItem();
                    if (item != Items.AIR) {
                        materials.merge(item, 1, Integer::sum);
                    }
                }
                case WRONG -> wrong++;
                case CORRECT -> correct++;
                case UNLOADED -> unloaded++;
            }
        }
        List<Map.Entry<Item, Integer>> top = new ArrayList<>(materials.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        topMaterials = top.size() > 6 ? List.copyOf(top.subList(0, 6)) : List.copyOf(top);
    }

    /** {missing, wrong, correct, unloaded} from the last full pass (for tests and the panel). */
    public int[] counts() {
        return new int[]{missing, wrong, correct, unloaded};
    }

    public Schematic schematic() {
        return schematic;
    }

    // ------------------------------------------------------------------ HUD

    private void renderHud(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (schematic == null || mc.options.hideGui) {
            return;
        }
        int total = missing + wrong + correct;
        String head = schematic.name() + (origin == null ? " · press Place Here" : "");
        String progress = total == 0 ? "Checking…" : String.format(Locale.ROOT, "%d / %d placed (%.0f%%)", correct, total, 100.0 * correct / total)
                + (wrong > 0 ? " · " + wrong + " wrong" : "") + (unloaded > 0 ? " · " + unloaded + " unloaded" : "");
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Item, Integer> m : topMaterials) {
            int stacks = m.getValue() / 64;
            lines.add(m.getKey().getName().getString() + "  " + m.getValue() + (stacks > 0 ? "  (" + stacks + " st + " + m.getValue() % 64 + ")" : ""));
        }
        float w = Math.max(GuiRenderUtil.textWidth(FontRole.SMALL, head), GuiRenderUtil.textWidth(FontRole.SMALL, progress));
        for (String l : lines) {
            w = Math.max(w, GuiRenderUtil.textWidth(FontRole.TINY, l));
        }
        w += 12f;
        float h = 28f + (lines.isEmpty() ? 0 : 4f + lines.size() * 8.5f) + 4f;
        float done = total == 0 ? 0f : correct / (float) total;
        ThemeManager.sync();
        hud.begin(e.graphics());
        try {
            GuiRenderUtil.roundRect(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.82f));
            GuiRenderUtil.roundOutline(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BORDER, 0.6f));
            GuiRenderUtil.text(FontRole.SMALL, head, 6f, 7f, ThemeManager.TEXT);
            GuiRenderUtil.text(FontRole.SMALL, progress, 6f, 17f, ThemeManager.TEXT_SECONDARY);
            GuiRenderUtil.roundRect(6f, 23f, w - 12f, 2f, 1f, ThemeManager.TRACK);
            GuiRenderUtil.roundRect(6f, 23f, (w - 12f) * done, 2f, 1f, ThemeManager.accent());
            float y = 32f;
            for (String l : lines) {
                GuiRenderUtil.text(FontRole.TINY, l, 6f, y, ThemeManager.TEXT_SECONDARY);
                y += 8.5f;
            }
            hud.size(w, h);
        } finally {
            hud.end();
        }
    }
}
