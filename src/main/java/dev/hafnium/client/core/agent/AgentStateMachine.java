package dev.hafnium.client.core.agent;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Lifecycle state of one agent: IDLE, PLANNING, RUNNING, PAUSED, RECOVERING, COMPLETED, FAILED.
 *
 * <p>Only transitions in the {@link TransitionTable} are accepted; anything else throws, so an agent bug cannot put
 * the machine in an impossible state. Exit hooks of the old state run before entry hooks of the new one. Every
 * transition is recorded (bounded history) and posted to the event bus as an {@link AgentStateEvent}.
 * State changes happen on the client thread.
 */
public class AgentStateMachine {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Agents");
    private static final int HISTORY_LIMIT = 64;

    private final String agentId;
    private final TransitionTable table;
    private final EventBus bus;
    private final ClientThread clientThread;
    private final LongSupplier clock;
    private final Map<AgentState, List<Consumer<AgentTransition>>> onEnter = new EnumMap<>(AgentState.class);
    private final Map<AgentState, List<Consumer<AgentTransition>>> onExit = new EnumMap<>(AgentState.class);
    private final CopyOnWriteArrayList<Consumer<AgentTransition>> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<AgentTransition> history = new ArrayDeque<>();

    private volatile AgentState state = AgentState.IDLE;
    private volatile String reason = "Created";
    private volatile long enteredAt;
    private boolean transitioning;

    /**
     * @param clock tick source for timestamps (e.g. {@code scheduler::currentTick})
     */
    public AgentStateMachine(String agentId, TransitionTable table, EventBus bus, ClientThread clientThread, LongSupplier clock) {
        this.agentId = Objects.requireNonNull(agentId);
        this.table = Objects.requireNonNull(table);
        this.bus = bus;
        this.clientThread = Objects.requireNonNull(clientThread);
        this.clock = Objects.requireNonNull(clock);
        for (AgentState s : AgentState.values()) {
            onEnter.put(s, new ArrayList<>());
            onExit.put(s, new ArrayList<>());
        }
        this.enteredAt = clock.getAsLong();
    }

    public String agentId() {
        return agentId;
    }

    public AgentState state() {
        return state;
    }

    /** Why the machine is in its current state (shown in diagnostics). */
    public String reason() {
        return reason;
    }

    public long ticksInState() {
        return clock.getAsLong() - enteredAt;
    }

    public boolean canTransition(AgentState to) {
        return table.allows(state, to);
    }

    /**
     * Moves to {@code to}.
     *
     * @throws IllegalStateException if the transition is not in the table, if called off the client thread, or if
     *                               called from inside another transition's hook
     */
    public void transition(AgentState to, String why) {
        if (!clientThread.isCurrent()) {
            throw new IllegalStateException(agentId + ": state changes must happen on the client thread");
        }
        if (transitioning) {
            throw new IllegalStateException(agentId + ": transition to " + to + " requested from inside a transition hook");
        }
        AgentState from = state;
        if (!table.allows(from, to)) {
            throw new IllegalStateException(agentId + ": illegal transition " + from + " -> " + to
                    + " (allowed: " + table.targets(from) + ")");
        }
        AgentTransition t = new AgentTransition(agentId, from, to, why == null ? "" : why, clock.getAsLong());
        transitioning = true;
        try {
            runHooks(onExit.get(from), t);
            state = to;
            reason = t.reason();
            enteredAt = t.tick();
            history.addLast(t);
            while (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
            runHooks(onEnter.get(to), t);
        } finally {
            transitioning = false;
        }
        for (Consumer<AgentTransition> l : listeners) {
            try {
                l.accept(t);
            } catch (RuntimeException e) {
                LOG.error("{}: transition listener failed", agentId, e);
            }
        }
        if (bus != null) {
            bus.post(new AgentStateEvent(t));
        }
    }

    /** Transition if legal; returns false (and does nothing) otherwise. */
    public boolean tryTransition(AgentState to, String why) {
        if (!canTransition(to)) {
            return false;
        }
        transition(to, why);
        return true;
    }

    public AgentStateMachine onEnter(AgentState s, Consumer<AgentTransition> hook) {
        onEnter.get(s).add(hook);
        return this;
    }

    public AgentStateMachine onExit(AgentState s, Consumer<AgentTransition> hook) {
        onExit.get(s).add(hook);
        return this;
    }

    public Runnable addListener(Consumer<AgentTransition> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public List<AgentTransition> history() {
        return List.copyOf(history);
    }

    private void runHooks(List<Consumer<AgentTransition>> hooks, AgentTransition t) {
        for (Consumer<AgentTransition> h : hooks) {
            try {
                h.accept(t);
            } catch (RuntimeException e) {
                // A hook failure must not leave the machine half-way; log and carry on.
                LOG.error("{}: hook for {} -> {} failed", agentId, t.from(), t.to(), e);
            }
        }
    }
}
