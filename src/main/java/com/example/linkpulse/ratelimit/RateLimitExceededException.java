package com.example.linkpulse.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many requests; try again in " + seconds(retryAfter) + " s");
        this.retryAfter = retryAfter;
    }

    // Retry-After is whole seconds, rounded up and never 0 (0 would invite an immediate retry).
    public long retryAfterSeconds() {
        return seconds(retryAfter);
    }

    private static long seconds(Duration d) {
        return Math.max(1, (d.toMillis() + 999) / 1000);
    }
}
