package dev.hafnium.client.modules.client;

import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.permission.Environment;
import dev.hafnium.client.core.permission.PermissionChangedEvent;
import dev.hafnium.client.core.permission.PermissionService;
import dev.hafnium.client.core.permission.ServerProfile;
import dev.hafnium.client.core.permission.ServerProfiles;
import dev.hafnium.client.core.setting.BoolSetting;

import java.util.EnumMap;
import java.util.Map;

/**
 * Settings-only editor for the {@link ServerProfile} of the multiplayer server you are on (or, when not on one, the
 * defaults for servers you have not configured yet). It records what the server's rules allow; it can only restrict
 * and never authorizes automation.
 */
public final class ServerProfileModule extends Module {
    public final BoolSetting assistanceAllowed = setting(new BoolSetting("Rules Allow Assist", false));
    private final Map<Capability, BoolSetting> prohibited = new EnumMap<>(Capability.class);

    private ServerProfiles profiles;
    private PermissionService permissions;
    private boolean loading;

    public ServerProfileModule() {
        super("Server Profile", Category.CLIENT, "What this server's rules allow. Restricts only; never authorizes"
                + " automation. Outside multiplayer it edits the defaults for new servers.");
        for (Capability c : Capability.values()) {
            prohibited.put(c, setting(new BoolSetting("Prohibit " + c.displayName(), false)));
        }
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public BoolSetting prohibitSetting(Capability c) {
        return prohibited.get(c);
    }

    /** Connects the settings to the profiles and the permission layer (called once by the client initializer). */
    public void bind(EventBus bus, ServerProfiles profiles, PermissionService permissions) {
        this.profiles = profiles;
        this.permissions = permissions;
        assistanceAllowed.addListener(v -> store());
        for (BoolSetting s : prohibited.values()) {
            s.addListener(v -> store());
        }
        bus.subscribe(PermissionChangedEvent.class, this, e -> {
            if (e.environmentChanged()) {
                show(editedProfile());
            }
        });
        show(editedProfile());
    }

    /** The profile these settings currently edit. */
    public ServerProfile editedProfile() {
        Environment env = permissions.environment();
        return env.kind() == Environment.Kind.REMOTE ? profiles.forServer(env.serverId()) : profiles.defaults();
    }

    private void show(ServerProfile p) {
        loading = true;
        try {
            assistanceAllowed.set(p.assistanceAllowed());
            for (Map.Entry<Capability, BoolSetting> e : prohibited.entrySet()) {
                e.getValue().set(p.prohibits(e.getKey()));
            }
        } finally {
            loading = false;
        }
    }

    private void store() {
        if (loading || profiles == null) {
            return;
        }
        ServerProfile p = editedProfile().withAssistanceAllowed(assistanceAllowed.get());
        for (Map.Entry<Capability, BoolSetting> e : prohibited.entrySet()) {
            p = p.withProhibited(e.getKey(), e.getValue().get());
        }
        profiles.put(p);
        permissions.updateProfile(p);
    }
}
