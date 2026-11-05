package com.example.linkpulse.click;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

// Redirects only enqueue; a scheduler thread writes the clicks in batches. Clicks still in the
// queue are lost if the process crashes (trade-off accepted for analytics, not for money).
@Component
@ConditionalOnProperty(name = "linkpulse.clicks.mode", havingValue = "async", matchIfMissing = true)
public class AsyncClickRecorder implements ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(AsyncClickRecorder.class);
    private static final String INSERT = "INSERT INTO clicks (code, clicked_at, referrer) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final BlockingQueue<Click> queue;
    private final int batchSize;
    private final Counter written;
    private final Counter dropped;
    private final Counter failed;

    public AsyncClickRecorder(JdbcTemplate jdbc,
                              MeterRegistry registry,
                              @Value("${linkpulse.clicks.queue-capacity}") int queueCapacity,
                              @Value("${linkpulse.clicks.batch-size}") int batchSize) {
        this.jdbc = jdbc;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.written = Counter.builder("linkpulse.clicks").tag("result", "written").register(registry);
        this.dropped = Counter.builder("linkpulse.clicks").tag("result", "dropped").register(registry);
        this.failed = Counter.builder("linkpulse.clicks").tag("result", "failed").register(registry);
        Gauge.builder("linkpulse.clicks.queue.size", queue, BlockingQueue::size).register(registry);
    }

    @Override
    public void record(String code, String referrer) {
        // offer() never blocks: when the queue is full we drop the click instead of slowing the redirect.
        if (!queue.offer(new Click(code, Instant.now(), ClickRecorder.truncate(referrer)))) {
            dropped.increment();
        }
    }

    @Scheduled(fixedDelayString = "${linkpulse.clicks.flush-interval-ms}")
    public void flush() {
        List<Click> batch = new ArrayList<>(batchSize);
        while (queue.drainTo(batch, batchSize) > 0) {
            write(batch);
            batch.clear();
        }
    }

    private void write(List<Click> batch) {
        try {
            jdbc.batchUpdate(INSERT, batch, batch.size(), (ps, click) -> {
                ps.setString(1, click.code());
                ps.setTimestamp(2, Timestamp.from(click.clickedAt()));
                ps.setString(3, click.referrer());
            });
            written.increment(batch.size());
        } catch (DataAccessException e) {
            failed.increment(batch.size());
            log.warn("Lost {} clicks: {}", batch.size(), e.getMessage());
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }
}