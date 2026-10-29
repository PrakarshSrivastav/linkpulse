package com.example.linkpulse.link;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class LinkCache {

    static final String KEY_PREFIX = "link:";
    static final String NOT_FOUND = "!";

    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Duration notFoundTtl;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public LinkCache(StringRedisTemplate redis,
                     MeterRegistry meterRegistry,
                     @Value("${linkpulse.cache.ttl}") Duration ttl,
                     @Value("${linkpulse.cache.not-found-ttl}") Duration notFoundTtl) {
        this.redis = redis;
        this.ttl = ttl;
        this.notFoundTtl = notFoundTtl;
        this.hits = meterRegistry.counter("linkpulse.cache.requests", "result", "hit");
        this.misses = meterRegistry.counter("linkpulse.cache.requests", "result", "miss");
        this.errors = meterRegistry.counter("linkpulse.cache.requests", "result", "error");
    }

    /**
     * Empty means "not in the cache, ask the database" (also when Redis is unavailable).
     * Otherwise the value is either the long URL or NOT_FOUND.
     */
    public Optional<String> get(String code) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + code);
            (value == null ? misses : hits).increment();
            return Optional.ofNullable(value);
        } catch (DataAccessException e) {
            errors.increment();
            log.warn("Redis read failed for {}, falling back to database: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    public void putLink(Link link, Instant now) {
        Duration timeToLive = ttl;
        if (link.getExpiresAt() != null) {
            Duration untilExpiry = Duration.between(now, link.getExpiresAt());
            if (untilExpiry.compareTo(timeToLive) < 0) {
                timeToLive = untilExpiry;
            }
        }
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            return;
        }
        set(link.getCode(), link.getLongUrl(), timeToLive);
    }

    public void putNotFound(String code) {
        set(code, NOT_FOUND, notFoundTtl);
    }

    public void evict(String code) {
        try {
            redis.delete(KEY_PREFIX + code);
        } catch (DataAccessException e) {
            log.warn("Redis evict failed for {}: {}", code, e.getMessage());
        }
    }

    private void set(String code, String value, Duration timeToLive) {
        try {
            redis.opsForValue().set(KEY_PREFIX + code, value, timeToLive);
        } catch (DataAccessException e) {
            log.warn("Redis write failed for {}: {}", code, e.getMessage());
        }
    }
}