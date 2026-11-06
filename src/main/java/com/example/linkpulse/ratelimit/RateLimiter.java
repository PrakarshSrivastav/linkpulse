package com.example.linkpulse.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class RateLimiter {

    static final String KEY_PREFIX = "rl:";
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final Counter allowed;
    private final Counter limited;
    private final Counter errors;

    public RateLimiter(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(loadScript(), List.class);
        this.allowed = meterRegistry.counter("linkpulse.ratelimit", "result", "allowed");
        this.limited = meterRegistry.counter("linkpulse.ratelimit", "result", "limited");
        this.errors = meterRegistry.counter("linkpulse.ratelimit", "result", "error");
    }

    public record Decision(boolean allowed, Duration retryAfter) {
        static final Decision ALLOW = new Decision(true, Duration.ZERO);
    }

    // Fails open: if Redis is down, the request goes through unlimited. Locking every user out of
    // login because the cache is unavailable would be worse than briefly losing the limit.
    public Decision tryAcquire(String bucket, RateLimitProperties.Rule rule) {
        try {
            List<?> result = redis.execute(script, List.of(KEY_PREFIX + bucket),
                    String.valueOf(rule.capacity()), String.valueOf(rule.per().toMillis()));
            if (result != null && ((Number) result.get(0)).longValue() == 1L) {
                allowed.increment();
                return Decision.ALLOW;
            }
            limited.increment();
            long retryMs = result == null ? rule.per().toMillis() : ((Number) result.get(1)).longValue();
            return new Decision(false, Duration.ofMillis(retryMs));
        } catch (DataAccessException e) {
            errors.increment();
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return Decision.ALLOW;
        }
    }

    private static String loadScript() {
        try {
            return new ClassPathResource("scripts/token-bucket.lua").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("token-bucket.lua missing from the classpath", e);
        }
    }
}
