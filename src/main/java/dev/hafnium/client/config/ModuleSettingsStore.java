package dev.hafnium.client.config;

import com.google.gson.*;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.setting.*;
import java.nio.file.Path;

/** Persists values without automatically enabling modules or starting actions. */
public final class ModuleSettingsStore {
    private static final Gson GSON = new Gson();
    private final HafniumCore core;
    private final DeferredJsonFile file;
    public ModuleSettingsStore(HafniumCore core, Path path) {
        this.core = core;
        file = new DeferredJsonFile(path);
    }
    public void attach() {
        try {
            String text = file.read();
            JsonObject root = text == null ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
            for (var m : core.modules().all())
                if (root.has(m.id())) {
                    var values = root.getAsJsonObject(m.id());
                    for (var s : m.settings())
                        if (values.has(s.id()))
                            try {
                                read(s, values.get(s.id()));
                            } catch (RuntimeException ignored) {
                            }
                }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("Hafnium/Settings").warn("Cannot load settings", e);
        }
        for (var m : core.modules().all())
            for (var s : m.settings()) s.addListener(v -> save());
        core.events().subscribe(dev.hafnium.client.core.event.ClientTickEvent.class, this, e -> {
            if (e == dev.hafnium.client.core.event.ClientTickEvent.END)
                file.tick(core.scheduler());
        });
    }
    private void save() {
        JsonObject root = new JsonObject();
        for (var m : core.modules().all()) {
            JsonObject values = new JsonObject();
            for (var s : m.settings()) {
                if (s instanceof KeybindSetting k) {
                    JsonObject key = new JsonObject();
                    key.addProperty("code", k.get());
                    key.addProperty("name", k.keyName());
                    values.add(s.id(), key);
                } else
                    values.add(s.id(), GSON.toJsonTree(s.get()));
            }
            root.add(m.id(), values);
        }
        file.changed(root.toString(), core.scheduler().currentTick());
    }
    private static void read(Setting<?> s, JsonElement v) {
        if (s instanceof KeybindSetting k)
            k.bind(v.getAsJsonObject().get("code").getAsInt(), v.getAsJsonObject().get("name").getAsString());
        else if (s instanceof BoolSetting b)
            b.set(v.getAsBoolean());
        else if (s instanceof NumberSetting n)
            n.set(v.getAsDouble());
        else if (s instanceof ColorSetting c)
            c.set(v.getAsInt());
        else if (s instanceof ModeSetting m)
            m.set(v.getAsString());
        else if (s instanceof ListSetting l)
            l.set(v.getAsString());
    }
}
