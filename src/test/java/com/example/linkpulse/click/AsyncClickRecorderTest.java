package com.example.linkpulse.click;

import com.example.linkpulse.link.Link;
import com.example.linkpulse.link.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Flush interval of 1 hour: the scheduler runs once at startup and then never during the test,
// so only our explicit flush() writes. No sleeping, no flaky timing.
@SpringBootTest(properties = "linkpulse.clicks.flush-interval-ms=3600000")
@AutoConfigureMockMvc
class AsyncClickRecorderTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AsyncClickRecorder recorder;

    @Autowired
    LinkRepository links;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        recorder.flush();
        jdbc.update("TRUNCATE clicks");
        links.deleteAllInBatch();
        redis.delete(redis.keys("link:*"));
    }

    @Test
    void redirectsAreQueuedAndOnlyWrittenOnFlush() throws Exception {
        links.saveAndFlush(new Link("async01", "https://example.com", null));

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/async01")).andExpect(status().isFound());
        }
        assertThat(clicksFor("async01")).isZero();

        recorder.flush();

        assertThat(clicksFor("async01")).isEqualTo(3);
    }

    @Test
    void failedRedirectsAreNotCounted() throws Exception {
        mvc.perform(get("/missing1")).andExpect(status().isNotFound());

        recorder.flush();

        assertThat(clicksFor("missing1")).isZero();
    }

    private long clicksFor(String code) {
        return jdbc.queryForObject("SELECT count(*) FROM clicks WHERE code = ?", Long.class, code);
    }
}