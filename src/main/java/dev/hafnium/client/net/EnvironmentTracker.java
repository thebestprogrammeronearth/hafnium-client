package dev.hafnium.client.net;

import dev.hafnium.client.core.permission.Environment;
import dev.hafnium.client.core.permission.PermissionService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.permissions.Permissions;
import dev.hafnium.client.core.permission.ServerPrivileges;
import dev.hafnium.client.core.permission.ServerProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the {@link PermissionService} where we are playing and what the server allows, based only on facts the game
 * establishes: whether this client runs the integrated server, which remote server it is connected to, and the
 * operator level and game mode the server assigned to the player. Also receives the optional Hafnium server policy;
 * servers without Hafnium never send one, and nothing waits for it.
 */
public final class EnvironmentTracker {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Permissions");

    private final PermissionService permissions;
    private final ServerProfiles profiles;

    public EnvironmentTracker(PermissionService permissions, ServerProfiles profiles) {
        this.permissions = permissions;
        this.profiles = profiles;
    }

    public void register() {
        PayloadTypeRegistry.playS2C().register(AutomationPolicyPayload.TYPE, AutomationPolicyPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(AutomationPolicyPayload.TYPE, (payload, context) -> {
            if (payload.version() != AutomationPolicyPayload.VERSION) {
                LOG.warn("Ignoring automation policy with unsupported version {}", payload.version());
                return;
            }
            context.client().execute(() -> permissions.applyServerPolicy(payload.capabilities()));
        });
    }

    public void onJoin(Minecraft mc) {
        Environment env = describe(mc);
        permissions.setEnvironment(env, env.kind() == Environment.Kind.REMOTE ? profiles.forServer(env.serverId()) : null);
    }

    /** Every client tick: mirror the privileges the server currently reports for the player. */
    public void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || permissions.environment().kind() == Environment.Kind.NONE) {
            return;
        }
        permissions.setPrivileges(new ServerPrivileges(
                player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
                player.isCreative()));
    }

    public void onDisconnect() {
        permissions.setEnvironment(Environment.NONE);
    }

    static Environment describe(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            // This client is running the server: singleplayer, or a LAN world it opened.
            return new Environment(Environment.Kind.INTEGRATED_HOST,
                    "local:" + mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        ServerData server = mc.getCurrentServer();
        return new Environment(Environment.Kind.REMOTE, server != null ? server.ip : "unknown-server");
    }
}
