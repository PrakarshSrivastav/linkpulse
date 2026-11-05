package com.example.linkpulse.click;

import com.example.linkpulse.click.ClickStats.DayCount;
import com.example.linkpulse.click.ClickStats.ReferrerCount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class ClickStatsRepository {

    static final Duration PER_DAY_WINDOW = Duration.ofDays(7);
    static final int TOP_REFERRERS = 5;

    private final JdbcTemplate jdbc;

    public ClickStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Only clicks since the link was created: a deleted code can be reused by someone else,
    // and the new owner must not inherit the old link's clicks.
    public ClickStats statsFor(String code, Instant linkCreatedAt) {
        Timestamp since = Timestamp.from(linkCreatedAt);
        Instant weekAgo = Instant.now().minus(PER_DAY_WINDOW);
        Timestamp perDaySince = Timestamp.from(weekAgo.isAfter(linkCreatedAt) ? weekAgo : linkCreatedAt);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM clicks WHERE code = ? AND clicked_at >= ?",
                Long.class, code, since);

        List<DayCount> perDay = jdbc.query("""
                        SELECT (clicked_at AT TIME ZONE 'UTC')::date AS day, count(*) AS click_count
                        FROM clicks
                        WHERE code = ? AND clicked_at >= ?
                        GROUP BY day
                        ORDER BY day
                        """,
                (rs, row) -> new DayCount(rs.getObject("day", LocalDate.class), rs.getLong("click_count")),
                code, perDaySince);

        List<ReferrerCount> topReferrers = jdbc.query("""
                        SELECT referrer, count(*) AS click_count
                        FROM clicks
                        WHERE code = ? AND clicked_at >= ? AND referrer IS NOT NULL
                        GROUP BY referrer
                        ORDER BY click_count DESC, referrer
                        LIMIT ?
                        """,
                (rs, row) -> new ReferrerCount(rs.getString("referrer"), rs.getLong("click_count")),
                code, since, TOP_REFERRERS);

        return new ClickStats(code, total, perDay, topReferrers);
    }
}