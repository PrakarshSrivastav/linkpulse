package com.example.linkpulse.link;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LinkCacheTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LinkRepository repository;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        repository.deleteAllInBatch();
        redis.delete(redis.keys(LinkCache.KEY_PREFIX + "*"));
    }

    @Test
    void secondRedirectIsServedFromCache() throws Exception {
        repository.saveAndFlush(new Link("cache01", "https://example.com/cached", null));

        mvc.perform(get("/cache01")).andExpect(status().isFound());

        repository.deleteAllInBatch();

        mvc.perform(get("/cache01"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/cached"));
    }

    @Test
    void notFoundIsCachedAndClearedWhenLinkIsCreated() throws Exception {
        mvc.perform(get("/promo-x")).andExpect(status().isNotFound());
        assertThat(redis.opsForValue().get("link:promo-x")).isEqualTo(LinkCache.NOT_FOUND);

        mvc.perform(post("/api/links").contentType(APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/promo", "alias": "promo-x"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/promo-x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/promo"));
    }

    @Test
    void cacheEntryNeverOutlivesLinkExpiry() throws Exception {
        repository.saveAndFlush(new Link("soon001", "https://example.com", Instant.now().plusSeconds(5)));

        mvc.perform(get("/soon001")).andExpect(status().isFound());

        Long secondsLeft = redis.getExpire("link:soon001", TimeUnit.SECONDS);
        assertThat(secondsLeft).isBetween(1L, 5L);
    }
}