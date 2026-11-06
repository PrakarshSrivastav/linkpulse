package com.example.linkpulse.ratelimit;

import com.example.linkpulse.link.LinkRepository;
import com.example.linkpulse.user.User;
import com.example.linkpulse.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Small buckets (3 per hour) so a few requests exhaust them; nothing refills during the test.
@SpringBootTest(properties = {
        "linkpulse.rate-limit.enabled=true",
        "linkpulse.rate-limit.login.capacity=3", "linkpulse.rate-limit.login.per=1h",
        "linkpulse.rate-limit.create-link.capacity=3", "linkpulse.rate-limit.create-link.per=1h"
})
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LinkRepository links;

    @Autowired
    UserRepository users;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        links.deleteAllInBatch();
        users.deleteAllInBatch();
        redis.delete(redis.keys(RateLimiter.KEY_PREFIX + "*"));
        redis.delete(redis.keys("link:*"));
    }

    @Test
    void fourthLoginFromOneIpGets429WithRetryAfterWhileAnotherIpIsUnaffected() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("198.51.100.1").andExpect(status().isUnauthorized());
        }
        login("198.51.100.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")));

        // Different IP, different bucket: still reaches the real login (401 for bad credentials).
        login("198.51.100.2").andExpect(status().isUnauthorized());
    }

    @Test
    void linkCreationIsLimitedPerUserNotGlobally() throws Exception {
        User alice = users.saveAndFlush(new User("alice@example.com", "not-a-real-hash"));
        User bob = users.saveAndFlush(new User("bob@example.com", "not-a-real-hash"));

        for (int i = 0; i < 3; i++) {
            create(alice).andExpect(status().isCreated());
        }
        create(alice).andExpect(status().isTooManyRequests());
        create(bob).andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions login(String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(fromIp(ip)).contentType(APPLICATION_JSON)
                .content("""
                        {"email": "nobody@example.com", "password": "wrong-password"}
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions create(User owner) throws Exception {
        return mvc.perform(post("/api/links").with(jwt().jwt(t -> t.subject(owner.getId().toString())))
                .contentType(APPLICATION_JSON)
                .content("""
                        {"url": "https://example.com"}
                        """));
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
