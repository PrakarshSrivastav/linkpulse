package com.example.linkpulse.click;

import com.example.linkpulse.link.Link;
import com.example.linkpulse.link.LinkRepository;
import com.example.linkpulse.user.User;
import com.example.linkpulse.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClickStatsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LinkRepository links;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    User alice;
    User bob;
    Instant now;

    @BeforeEach
    void clean() {
        jdbc.update("TRUNCATE clicks");
        links.deleteAllInBatch();
        users.deleteAllInBatch();
        redis.delete(redis.keys("link:*"));
        alice = users.saveAndFlush(new User("alice@example.com", "not-a-real-hash"));
        bob = users.saveAndFlush(new User("bob@example.com", "not-a-real-hash"));
        now = Instant.now();
    }

    @Test
    void statsCountOnlyThisLinksClicksSinceItWasCreated() throws Exception {
        links.saveAndFlush(new Link("stat-01", "https://example.com", null, alice.getId()));
        // Pretend the link was created 3 days ago, so we can place clicks on earlier days.
        jdbc.update("UPDATE links SET created_at = ? WHERE code = 'stat-01'", ts(daysAgo(3)));

        click("stat-01", daysAgo(4), "https://old.example");   // before creation: an older link with the same code
        click("stat-01", daysAgo(2), "https://twitter.com/a");
        click("stat-01", daysAgo(2), null);
        click("stat-01", daysAgo(1), "https://twitter.com/a");
        click("stat-01", now, "https://news.example");
        click("other-1", now, "https://twitter.com/a");         // a different link

        mvc.perform(get("/api/links/stat-01/stats").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.perDay.length()").value(3))
                .andExpect(jsonPath("$.perDay[0].clicks").value(2))
                .andExpect(jsonPath("$.perDay[1].clicks").value(1))
                .andExpect(jsonPath("$.perDay[2].clicks").value(1))
                .andExpect(jsonPath("$.topReferrers.length()").value(2))
                .andExpect(jsonPath("$.topReferrers[0].referrer").value("https://twitter.com/a"))
                .andExpect(jsonPath("$.topReferrers[0].clicks").value(2))
                .andExpect(jsonPath("$.topReferrers[1].referrer").value("https://news.example"));
    }

    @Test
    void otherUsersGet404ForMyStats() throws Exception {
        links.saveAndFlush(new Link("stat-02", "https://example.com", null, alice.getId()));

        mvc.perform(get("/api/links/stat-02/stats").with(as(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void statsRequireLogin() throws Exception {
        links.saveAndFlush(new Link("stat-03", "https://example.com", null, alice.getId()));

        mvc.perform(get("/api/links/stat-03/stats"))
                .andExpect(status().isUnauthorized());
    }

    private void click(String code, Instant at, String referrer) {
        jdbc.update("INSERT INTO clicks (code, clicked_at, referrer) VALUES (?, ?, ?)", code, ts(at), referrer);
    }

    private Instant daysAgo(int days) {
        return now.minus(Duration.ofDays(days));
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }
}