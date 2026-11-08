package dev.hafnium.client.core.task;

/**
 * Bounded retries with exponential backoff, in ticks.
 *
 * @param maxAttempts total attempts including the first (1 = never retry)
 */
public record RetryPolicy(int maxAttempts, int initialBackoffTicks, double multiplier, int maxBackoffTicks) {
    public static final RetryPolicy NONE = new RetryPolicy(1, 0, 1, 0);

    public RetryPolicy {
        if (maxAttempts < 1 || initialBackoffTicks < 0 || multiplier < 1 || maxBackoffTicks < initialBackoffTicks) {
            throw new IllegalArgumentException("Invalid retry policy: " + maxAttempts + " attempts, backoff "
                    + initialBackoffTicks + " x" + multiplier + " max " + maxBackoffTicks);
        }
    }

    public static RetryPolicy attempts(int maxAttempts, int backoffTicks) {
        return new RetryPolicy(maxAttempts, backoffTicks, 2.0, Math.max(backoffTicks, backoffTicks * 8));
    }

    /** Backoff before attempt number {@code nextAttempt} (2 = first retry). */
    public int backoffBefore(int nextAttempt) {
        double b = initialBackoffTicks * Math.pow(multiplier, Math.max(0, nextAttempt - 2));
        return (int) Math.min(maxBackoffTicks, Math.round(b));
    }
}
