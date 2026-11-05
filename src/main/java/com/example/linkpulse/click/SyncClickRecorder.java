package com.example.linkpulse.click;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.sql.Timestamp;
import java.time.Instant;

// Naive version: one INSERT per redirect, on the request thread. Kept to measure what it costs.
@Component
@ConditionalOnProperty(name = "linkpulse.clicks.mode", havingValue = "sync")
public class SyncClickRecorder implements ClickRecorder {

    private final JdbcTemplate jdbc;

    public SyncClickRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String code, String referrer) {
        jdbc.update("INSERT INTO clicks (code, clicked_at, referrer) VALUES (?, ?, ?)",
                code, Timestamp.from(Instant.now()), ClickRecorder.truncate(referrer));
    }
}