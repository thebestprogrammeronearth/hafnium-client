package dev.hafnium.client.core.permission;

import dev.hafnium.client.core.control.ControlEvent;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.task.TaskEvent;
import dev.hafnium.client.core.task.TaskScheduler;
import dev.hafnium.client.core.task.TaskState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Turns permission changes, mode changes and permission denials into short user-facing notices (shown in chat by the
 * game adapter): what is unavailable on this server and why, and that server rules still apply. Pure logic; the
 * {@link #setSink sink} decides how notices are displayed.
 */
public final class PermissionNotices {
    public enum Level { INFO, WARNING, ERROR }

    public record Notice(Level level, String text) {
    }

    private static final int HISTORY = 32;
    private static final long REPEAT_SUPPRESS_MILLIS = 3000;

    private final PermissionService permissions;
    private final ArrayDeque<Notice> recent = new ArrayDeque<>();
    private volatile Consumer<Notice> sink = n -> { };
    private ServerPolicy lastPolicy;
    private ServerPrivileges lastPrivileges = ServerPrivileges.NONE;
    private String lastText;
    private long lastAt;

    public PermissionNotices(EventBus bus, PermissionService permissions) {
        this.permissions = permissions;
        bus.subscribe(PermissionChangedEvent.class, this, this::onPermissions);
        bus.subscribe(ControlEvent.ModeChanged.class, this, this::onMode);
        bus.subscribe(TaskEvent.class, this, this::onTask);
    }

    public void setSink(Consumer<Notice> sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    /** Most recent notices, oldest first. */
    public synchronized List<Notice> recent() {
        return new ArrayList<>(recent);
    }

    private void onPermissions(PermissionChangedEvent e) {
        Environment env = e.environment();
        if (e.environmentChanged()) {
            lastPolicy = null;
            lastPrivileges = ServerPrivileges.NONE;
            if (env.kind() == Environment.Kind.REMOTE) {
                emit(Level.WARNING, remoteSummary(env, e.profile()));
            } else if (env.kind() == Environment.Kind.INTEGRATED_HOST) {
                emit(Level.INFO, "You are hosting this world: automation is authorized here. Mode: Manual.");
            }
            return;
        }
        if (e.serverPolicy() != null && e.serverPolicy() != lastPolicy) {
            lastPolicy = e.serverPolicy();
            if (e.serverPolicy().allowed().isEmpty()) {
                emit(Level.WARNING, env.serverId() + " revoked all Hafnium automation; affected tasks were stopped.");
            } else {
                emit(Level.INFO, env.serverId() + " authorized: " + ids(e.serverPolicy().allowed())
                        + ". Everything else stays unavailable on this server.");
            }
        }
        if (!e.privileges().equals(lastPrivileges)) {
            ServerPrivileges p = e.privileges();
            lastPrivileges = p;
            emit(Level.INFO, "Server privileges: operator " + (p.operatorCommands() ? "yes" : "no")
                    + ", creative " + (p.creative() ? "yes" : "no") + ".");
        }
    }

    private static String remoteSummary(Environment env, ServerProfile profile) {
        StringBuilder sb = new StringBuilder("Connected to ").append(env.serverId())
                .append(" with no Hafnium authorization: autonomous automation and player combat are unavailable. ");
        if (profile != null && profile.assistanceAllowed()) {
            sb.append("Gameplay assistance is allowed by your Server Profile. ");
        } else {
            sb.append("Gameplay assistance is off until you confirm this server's rules allow it "
                    + "(ClickGUI > Client > Server Profile). ");
        }
        if (profile != null && !profile.prohibited().isEmpty()) {
            sb.append("Prohibited here by your profile: ").append(ids(profile.prohibited())).append(". ");
        }
        sb.append("Client-side features work normally. Server rules and anticheat still apply; "
                + "Hafnium cannot prevent penalties.");
        return sb.toString();
    }

    private void onMode(ControlEvent.ModeChanged e) {
        String text = "Mode: " + display(e.current()) + ".";
        Environment env = permissions.environment();
        if (e.current() == OperatingMode.AUTONOMOUS && env.kind() == Environment.Kind.REMOTE
                && permissions.serverPolicy() == null) {
            emit(Level.WARNING, text + " " + env.serverId()
                    + " has not authorized automation, so nothing can run autonomously here.");
        } else {
            emit(Level.INFO, text);
        }
    }

    private void onTask(TaskEvent e) {
        if (e.current() == TaskState.FAILED && e.task().error() != null
                && e.task().error().startsWith(TaskScheduler.NOT_PERMITTED_PREFIX)) {
            String reason = e.task().error().substring(TaskScheduler.NOT_PERMITTED_PREFIX.length());
            String verb = e.previous() == TaskState.RUNNING || e.previous() == TaskState.SUSPENDED
                    ? " stopped: " : " unavailable: ";
            emit(Level.ERROR, e.task().name() + verb + reason);
        }
    }

    private void emit(Level level, String text) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (text.equals(lastText) && now - lastAt < REPEAT_SUPPRESS_MILLIS) {
                return;
            }
            lastText = text;
            lastAt = now;
            recent.addLast(new Notice(level, text));
            while (recent.size() > HISTORY) {
                recent.removeFirst();
            }
        }
        sink.accept(new Notice(level, text));
    }

    private static String ids(Collection<Capability> caps) {
        StringJoiner j = new StringJoiner(", ");
        for (Capability c : caps.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(caps)) {
            j.add(c.id());
        }
        return j.toString();
    }

    private static String display(OperatingMode m) {
        return switch (m) {
            case MANUAL -> "Manual";
            case ASSIST -> "Assist";
            case AUTONOMOUS -> "Autonomous";
        };
    }
}
