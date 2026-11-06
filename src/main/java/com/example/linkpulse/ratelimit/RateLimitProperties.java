package com.example.linkpulse.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Bound from linkpulse.rate-limit.* in application.yml.
@ConfigurationProperties("linkpulse.rate-limit")
public record RateLimitProperties(boolean enabled, Rule login, Rule register, Rule createLink) {

    // capacity requests in a burst, refilled evenly over `per` (5 per 10m = one every 2 minutes).
    public record Rule(int capacity, Duration per) {}
}
