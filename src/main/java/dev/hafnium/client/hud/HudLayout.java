package dev.hafnium.client.hud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hafnium.client.config.DeferredJsonFile;
import dev.hafnium.client.core.HafniumCore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/** Every movable overlay, and where the user put them (config/hafnium/hud-layout.json). Client thread only. */
public final class HudLayout {
    public record Placement(float ax, float ay, float ox, float oy, float scale) {
    }

    private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();
    private static final Map<String, Placement> PLACEMENTS = new HashMap<>();
    private static DeferredJsonFile file;

    private HudLayout() {
    }

    public static void register(HudElement element) {
        ELEMENTS.put(element.id(), element);
    }

    public static List<HudElement> elements() {
        return Collections.unmodifiableList(new ArrayList<>(ELEMENTS.values()));
    }

    public static HudElement element(String id) {
        return ELEMENTS.get(id);
    }

    static Placement placement(String id) {
        return PLACEMENTS.get(id);
    }

    static void put(String id, Placement p) {
        PLACEMENTS.put(id, p);
        save();
    }

    static void remove(String id) {
        if (PLACEMENTS.remove(id) != null) {
            save();
        }
    }

    /** Loads saved placements; called once at startup. */
    public static void load(Path path) {
        file = new DeferredJsonFile(path);
        try {
            String json = file.read();
            if (json == null) {
                return;
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (var entry : root.entrySet()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                PLACEMENTS.put(entry.getKey(), new Placement(o.get("ax").getAsFloat(), o.get("ay").getAsFloat(),
                        o.get("ox").getAsFloat(), o.get("oy").getAsFloat(), o.get("scale").getAsFloat()));
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("Hafnium/HUD").warn("Could not read the HUD layout; using defaults", e);
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        file.changed(new Gson().toJson(PLACEMENTS), HafniumCore.get().scheduler().currentTick());
    }
}
