package dev.hafnium.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.permission.ServerProfile;
import dev.hafnium.client.core.permission.ServerProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Persists {@link ServerProfiles} as JSON (written atomically on every change). A missing or unreadable file leaves
 * the safe defaults in place: assistance off, nothing confirmed.
 */
public final class ServerProfileStore {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Profiles");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int VERSION = 1;

    private final Path file;

    public ServerProfileStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Loads the file into {@code profiles} and saves back on every later change. */
    public void attach(ServerProfiles profiles) {
        load(profiles);
        profiles.onChange(this::save);
    }

    public void load(ServerProfiles profiles) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            ServerProfile defaults = root.has("defaults")
                    ? read(ServerProfile.DEFAULT_ID, root.getAsJsonObject("defaults")) : ServerProfile.DEFAULTS;
            List<ServerProfile> all = new ArrayList<>();
            if (root.has("servers")) {
                for (var entry : root.getAsJsonObject("servers").entrySet()) {
                    all.add(read(entry.getKey(), entry.getValue().getAsJsonObject()));
                }
            }
            profiles.load(defaults, all);
            LOG.info("Loaded {} server profiles from {}", all.size(), file);
        } catch (RuntimeException | IOException e) {
            LOG.error("Could not read server profiles from {}; using safe defaults", file, e);
        }
    }

    public void save(ServerProfiles profiles) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("note", "Records what each server's rules allow. Profiles can only restrict: they never "
                + "authorize autonomous automation, player combat or privileged actions.");
        root.add("defaults", write(profiles.defaults()));
        JsonObject servers = new JsonObject();
        for (ServerProfile p : profiles.all()) {
            servers.add(p.serverId(), write(p));
        }
        root.add("servers", servers);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Could not save server profiles to {}", file, e);
        }
    }

    private static ServerProfile read(String id, JsonObject o) {
        boolean assistance = o.has("assistanceAllowed") && o.get("assistanceAllowed").getAsBoolean();
        EnumSet<Capability> prohibited = EnumSet.noneOf(Capability.class);
        if (o.has("prohibited")) {
            for (JsonElement e : o.getAsJsonArray("prohibited")) {
                Capability c = Capability.fromId(e.getAsString());
                if (c != null) {
                    prohibited.add(c);
                }
            }
        }
        return new ServerProfile(id, assistance, prohibited);
    }

    private static JsonObject write(ServerProfile p) {
        JsonObject o = new JsonObject();
        o.addProperty("assistanceAllowed", p.assistanceAllowed());
        JsonArray arr = new JsonArray();
        for (Capability c : Capability.values()) {
            if (p.prohibits(c)) {
                arr.add(c.id());
            }
        }
        o.add("prohibited", arr);
        return o;
    }
}
