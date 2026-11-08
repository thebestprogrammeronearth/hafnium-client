package dev.hafnium.client.core.agent;

/** One state change of an agent. {@code tick} is the scheduler tick at which it happened. */
public record AgentTransition(String agentId, AgentState from, AgentState to, String reason, long tick) {
}
