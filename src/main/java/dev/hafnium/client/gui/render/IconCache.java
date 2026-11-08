package dev.hafnium.client.gui.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Rasterises each (icon, pixel size) pair once into a white alpha-mask texture; drawing tints it. */
public final class IconCache {
    private static final float STROKE = 2f;
    private static final Map<Long, Identifier> CACHE = new HashMap<>();

    private IconCache() {
    }

    public static Identifier get(Icon icon, int sizePx) {
        long key = ((long) icon.ordinal() << 32) | sizePx;
        Identifier id = CACHE.get(key);
        if (id == null) {
            id = Identifier.fromNamespaceAndPath("hafnium",
                    "dynamic/icon_" + icon.name().toLowerCase(Locale.ROOT) + "_" + sizePx);
            Minecraft.getInstance().getTextureManager().register(id, rasterise(icon, sizePx, id));
            CACHE.put(key, id);
        }
        return id;
    }

    private static DynamicTexture rasterise(Icon icon, int size, Identifier id) {
        NativeImage img = new NativeImage(size, size, true);
        float scale = size / 24f;
        // Keep strokes at least ~1.15 px wide so tiny icons don't break up.
        float halfStrokePx = Math.max(STROKE * scale, 1.15f) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = icon.shape.distance((x + 0.5f) / scale, (y + 0.5f) / scale) * scale;
                float cov = Math.max(0f, Math.min(1f, halfStrokePx - d + 0.5f));
                img.setPixel(x, y, (Math.round(cov * 255) << 24) | 0xFFFFFF);
            }
        }
        return new DynamicTexture(id::toString, img);
    }
}
