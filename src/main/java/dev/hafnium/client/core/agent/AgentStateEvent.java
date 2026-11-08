package dev.hafnium.client.core.agent;

/** Posted on the event bus after every agent state transition. */
public record AgentStateEvent(AgentTransition transition) {
}
