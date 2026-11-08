package dev.hafnium.client.core.permission;

import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Decides whether a task may exercise a {@link Capability} with a given {@link Autonomy} right now.
 *
 * <h2>Three categories</h2>
 * <ol>
 *   <li><b>Client-side features</b> — rendering, HUD, observation of received world data, navigation and path
 *       calculations, inventory analysis, combat analytics, planning, configuration. These declare no capability,
 *       never reach this class, and work everywhere without any server plugin.</li>
 *   <li><b>Server-compliant gameplay assistance</b> ({@link Autonomy#ASSISTED} use of a
 *       {@link Capability.Tier#GAMEPLAY} capability) — needs Assist or Autonomous mode, and on a remote server either
 *       a server policy allowing it or the user's {@link ServerProfile} confirming the server's rules allow
 *       assistance. A profile can also mark capabilities as prohibited on that server.</li>
 *   <li><b>Server-authorized automation</b> ({@link Autonomy#AUTONOMOUS} use, and any use of
 *       {@link Capability.Tier#AUTHORIZED_ONLY}) — needs Autonomous mode and authorization: hosting the world
 *       (singleplayer or your own LAN world) or an explicit grant over {@code hafnium:automation_policy}. Normal
 *       Minecraft packets are not taken as permission to automate.</li>
 * </ol>
 * {@link Capability.Tier#SERVER_PRIVILEGE} capabilities also require the vanilla privilege the server assigned
 * (operator level, creative mode).
 *
 * <p>Client-side settings and profiles can only <em>restrict</em>: no client toggle authorizes automation or
 * privileged actions. "Allowed" means only that Hafnium will not block the action; it does not mean a server's rules
 * or anticheat accept it, and nothing here prevents penalties.
 *
 * <p>This class does not schedule, navigate or observe anything; the scheduler consults it through
 * {@link PermissionGuard}. The operating mode is read through a supplier so the control layer stays independent.
 */
public final class PermissionService {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Permissions");

    private final EventBus bus;
    private final Supplier<OperatingMode> mode;
    private volatile Environment environment = Environment.NONE;
    private volatile ServerPolicy serverPolicy;
    private volatile ServerPrivileges privileges = ServerPrivileges.NONE;
    private volatile ServerProfile profile;
    private volatile Set<Capability> userRestrictions = EnumSet.noneOf(Capability.class);

    public PermissionService(EventBus bus, Supplier<OperatingMode> mode) {
        this.bus = bus;
        this.mode = Objects.requireNonNull(mode);
    }

    public Environment environment() {
        return environment;
    }

    /** The policy the current server sent, or null if it sent none (the normal case for servers without Hafnium). */
    public ServerPolicy serverPolicy() {
        return serverPolicy;
    }

    public ServerPrivileges privileges() {
        return privileges;
    }

    /** The user's profile for the current remote server, or null when not on a remote server. */
    public ServerProfile profile() {
        return profile;
    }

    public OperatingMode mode() {
        return mode.get();
    }

    /** Join/leave without a profile (singleplayer, tests). */
    public void setEnvironment(Environment env) {
        setEnvironment(env, null);
    }

    /**
     * Called by the game adapter on join/leave. A policy and privileges from a previous connection are discarded.
     *
     * @param serverProfile the user's profile for this server (used only for {@link Environment.Kind#REMOTE})
     */
    public synchronized void setEnvironment(Environment env, ServerProfile serverProfile) {
        Objects.requireNonNull(env);
        ServerProfile p = env.kind() == Environment.Kind.REMOTE
                ? (serverProfile != null ? serverProfile : ServerProfile.DEFAULTS.withServerId(env.serverId()))
                : null;
        boolean envChanged = !env.equals(environment);
        if (envChanged || !Objects.equals(p, profile)) {
            environment = env;
            profile = p;
            if (envChanged) {
                serverPolicy = null;
                privileges = ServerPrivileges.NONE;
                LOG.info("Environment: {} {}", env.kind(), env.serverId());
            }
            post(envChanged);
        }
    }

    /** Replaces the profile of the current remote server (the user edited it). Ignored when not on that server. */
    public synchronized void updateProfile(ServerProfile updated) {
        if (environment.kind() == Environment.Kind.REMOTE && updated != null
                && updated.serverId().equals(ServerProfile.normalize(environment.serverId()))
                && !updated.equals(profile)) {
            profile = updated;
            post(false);
        }
    }

    /**
     * Records the privileges the server currently reports for this player. Posts a change (and so re-checks running
     * tasks) only when they actually differ, so the game adapter can call this every tick.
     */
    public synchronized void setPrivileges(ServerPrivileges p) {
        Objects.requireNonNull(p);
        if (environment.kind() == Environment.Kind.NONE) {
            p = ServerPrivileges.NONE;
        }
        if (!p.equals(privileges)) {
            privileges = p;
            LOG.info("Server privileges: {}", p);
            post(false);
        }
    }

    /**
     * Records a policy received from the server we are connected to. Unknown capability ids are ignored.
     *
     * @return the accepted policy, or null if there is no connection to attach it to
     */
    public synchronized ServerPolicy applyServerPolicy(Collection<String> capabilityIds) {
        if (environment.kind() == Environment.Kind.NONE) {
            LOG.warn("Ignoring automation policy received outside a world");
            return null;
        }
        EnumSet<Capability> allowed = EnumSet.noneOf(Capability.class);
        for (String id : capabilityIds) {
            Capability c = Capability.fromId(id);
            if (c != null) {
                allowed.add(c);
            } else {
                LOG.debug("Ignoring unknown capability '{}' in server policy", id);
            }
        }
        serverPolicy = new ServerPolicy(environment.serverId(), allowed, System.currentTimeMillis());
        LOG.info("Server {} sent an automation policy allowing {}", environment.serverId(), allowed);
        post(false);
        return serverPolicy;
    }

    /** Capabilities the user has switched off everywhere. Can only remove permission, never add it. */
    public void setUserRestrictions(Set<Capability> restricted) {
        userRestrictions = restricted.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(restricted);
        post(false);
    }

    /** Decision for the current operating mode. */
    public Decision decide(Capability capability, Autonomy autonomy) {
        return decide(capability, autonomy, mode.get());
    }

    /** First denial among {@code capabilities}, or an allow decision if all are permitted. */
    public Decision decideAll(Set<Capability> capabilities, Autonomy autonomy) {
        OperatingMode m = mode.get();
        for (Capability c : capabilities) {
            Decision d = decide(c, autonomy, m);
            if (!d.allowed()) {
                return d;
            }
        }
        return Decision.allow("all capabilities permitted");
    }

    /** What the environment allows for {@code capability}, ignoring the operating mode (for the indicator). */
    public CapabilityStatus status(Capability capability) {
        return new CapabilityStatus(capability,
                decide(capability, Autonomy.ASSISTED, OperatingMode.AUTONOMOUS),
                decide(capability, Autonomy.AUTONOMOUS, OperatingMode.AUTONOMOUS));
    }

    public Map<Capability, CapabilityStatus> statuses() {
        Map<Capability, CapabilityStatus> out = new EnumMap<>(Capability.class);
        for (Capability c : Capability.values()) {
            out.put(c, status(c));
        }
        return out;
    }

    private Decision decide(Capability capability, Autonomy autonomy, OperatingMode m) {
        String name = capability.id();
        if (userRestrictions.contains(capability)) {
            return Decision.deny(name + " is disabled in your Hafnium settings");
        }
        Environment env = environment;
        if (env.kind() == Environment.Kind.NONE) {
            return Decision.deny("not in a world");
        }
        if (m == OperatingMode.MANUAL) {
            return Decision.deny("Manual mode: Hafnium performs no gameplay actions (switch to Assist or Autonomous)");
        }
        if (autonomy == Autonomy.AUTONOMOUS && m != OperatingMode.AUTONOMOUS) {
            return Decision.deny("autonomous " + name + " needs Autonomous mode (current mode: " + m + ")");
        }
        if (env.kind() == Environment.Kind.INTEGRATED_HOST) {
            // On a world we host, a policy could only come from a mod the host installed; hosting already decides.
            if (capability.tier() == Capability.Tier.SERVER_PRIVILEGE) {
                return privilegeDecision(capability, privileges);
            }
            return Decision.allow("you are hosting this world");
        }

        String server = env.serverId();
        ServerProfile prof = profile;
        if (prof != null && prof.prohibits(capability)) {
            return Decision.deny(name + " is marked prohibited on " + server + " in your Server Profile");
        }
        ServerPolicy policy = serverPolicy;
        if (policy != null && !policy.allowed().contains(capability)) {
            return Decision.deny(server + "'s automation policy does not allow " + name);
        }
        boolean authorized = policy != null; // and it lists the capability (checked above)
        if (capability.tier() == Capability.Tier.SERVER_PRIVILEGE) {
            Decision p = privilegeDecision(capability, privileges);
            if (!p.allowed()) {
                return p;
            }
        }
        if (authorized) {
            return Decision.allow("authorized by " + server + "'s automation policy");
        }
        if (autonomy == Autonomy.AUTONOMOUS || capability.tier() == Capability.Tier.AUTHORIZED_ONLY) {
            String what = capability.tier() == Capability.Tier.AUTHORIZED_ONLY ? name : "autonomous " + name;
            return Decision.deny(what + " requires authorization from " + server
                    + ", which has not sent any (normal gameplay packets are not treated as permission to automate)");
        }
        if (prof != null && prof.assistanceAllowed()) {
            return Decision.allow("assistance allowed by your Server Profile for " + server
                    + "; the server's rules and anticheat still apply");
        }
        return Decision.deny("gameplay assistance on " + server
                + " is off until you confirm in its Server Profile that the server's rules allow it");
    }

    private static Decision privilegeDecision(Capability capability, ServerPrivileges p) {
        switch (capability) {
            case OPERATOR_COMMANDS:
                return p.operatorCommands()
                        ? Decision.allow("the server gave you operator permission")
                        : Decision.deny("operator_commands requires operator permission (level 2) from the server");
            case CREATIVE_INVENTORY:
                return p.creative()
                        ? Decision.allow("the server put you in creative mode")
                        : Decision.deny("creative_inventory requires the server to put you in creative mode");
            default:
                return Decision.deny("no privilege rule for " + capability.id());
        }
    }

    private void post(boolean environmentChanged) {
        if (bus != null) {
            bus.post(new PermissionChangedEvent(environment, serverPolicy, privileges, profile, environmentChanged));
        }
    }
}
