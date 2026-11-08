package dev.hafnium.client.core.agent;

public enum AgentState {
    IDLE,
    PLANNING,
    RUNNING,
    PAUSED,
    RECOVERING,
    COMPLETED,
    FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
