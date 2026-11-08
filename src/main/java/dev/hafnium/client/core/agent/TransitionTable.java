package dev.hafnium.client.core.agent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which state changes are legal. {@link #standard()} is the default lifecycle; agents can start from it and
 * {@link Builder#allow allow} or {@link Builder#forbid forbid} extra edges.
 */
public final class TransitionTable {
    private final Map<AgentState, Set<AgentState>> edges;

    private TransitionTable(Map<AgentState, Set<AgentState>> edges) {
        this.edges = edges;
    }

    /**
     * <pre>
     * IDLE       -> PLANNING
     * PLANNING   -> RUNNING, PAUSED, FAILED, IDLE
     * RUNNING    -> PAUSED, RECOVERING, COMPLETED, FAILED, IDLE, PLANNING (re-plan)
     * PAUSED     -> RUNNING, PLANNING, IDLE, FAILED
     * RECOVERING -> RUNNING, PLANNING, PAUSED, FAILED, IDLE
     * COMPLETED  -> IDLE
     * FAILED     -> IDLE
     * </pre>
     */
    public static TransitionTable standard() {
        return builder()
                .allow(AgentState.IDLE, AgentState.PLANNING)
                .allow(AgentState.PLANNING, AgentState.RUNNING, AgentState.PAUSED, AgentState.FAILED, AgentState.IDLE)
                .allow(AgentState.RUNNING, AgentState.PAUSED, AgentState.RECOVERING, AgentState.COMPLETED,
                        AgentState.FAILED, AgentState.IDLE, AgentState.PLANNING)
                .allow(AgentState.PAUSED, AgentState.RUNNING, AgentState.PLANNING, AgentState.IDLE, AgentState.FAILED)
                .allow(AgentState.RECOVERING, AgentState.RUNNING, AgentState.PLANNING, AgentState.PAUSED,
                        AgentState.FAILED, AgentState.IDLE)
                .allow(AgentState.COMPLETED, AgentState.IDLE)
                .allow(AgentState.FAILED, AgentState.IDLE)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean allows(AgentState from, AgentState to) {
        return edges.get(from).contains(to);
    }

    public Set<AgentState> targets(AgentState from) {
        return Set.copyOf(edges.get(from));
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        for (Map.Entry<AgentState, Set<AgentState>> e : edges.entrySet()) {
            b.edges.get(e.getKey()).addAll(e.getValue());
        }
        return b;
    }

    public static final class Builder {
        private final Map<AgentState, EnumSet<AgentState>> edges = new EnumMap<>(AgentState.class);

        private Builder() {
            for (AgentState s : AgentState.values()) {
                edges.put(s, EnumSet.noneOf(AgentState.class));
            }
        }

        public Builder allow(AgentState from, AgentState... to) {
            for (AgentState t : to) {
                if (t == from) {
                    throw new IllegalArgumentException("Self-transition " + from + " is implicit and not allowed");
                }
                edges.get(from).add(t);
            }
            return this;
        }

        public Builder forbid(AgentState from, AgentState to) {
            edges.get(from).remove(to);
            return this;
        }

        public TransitionTable build() {
            Map<AgentState, Set<AgentState>> copy = new EnumMap<>(AgentState.class);
            for (Map.Entry<AgentState, EnumSet<AgentState>> e : edges.entrySet()) {
                copy.put(e.getKey(), Set.copyOf(e.getValue()));
            }
            return new TransitionTable(copy);
        }
    }
}
