package com.example.linkpulse.link;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LinkOwnershipTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LinkRepository repository;

    @Autowired
    UserRepository users;

    @Autowired
    StringRedisTemplate redis;

    User alice;
    User bob;

    @BeforeEach
    void clean() {
        repository.deleteAllInBatch();
        users.deleteAllInBatch();
        redis.delete(redis.keys(LinkCache.KEY_PREFIX + "*"));
        alice = users.saveAndFlush(new User("alice@example.com", "not-a-real-hash"));
        bob = users.saveAndFlush(new User("bob@example.com", "not-a-real-hash"));
    }

    @Test
    void creatingALinkRequiresLogin() throws Exception {
        mvc.perform(post("/api/links").contentType(APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    @Test
    void listShowsOnlyMyOwnLinks() throws Exception {
        create(alice, "alice-1");
        create(bob, "bob-1");

        mvc.perform(get("/api/links").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("alice-1"));
    }

    @Test
    void ownerCanDeleteAndTheCachedRedirectStopsImmediately() throws Exception {
        create(alice, "gone-01");
        mvc.perform(get("/gone-01")).andExpect(status().isFound());
        assertThat(redis.hasKey("link:gone-01")).isTrue();

        mvc.perform(delete("/api/links/gone-01").with(as(alice)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/gone-01")).andExpect(status().isNotFound());
    }

    @Test
    void otherUsersCannotDeleteMyLink() throws Exception {
        create(alice, "mine-01");

        // 404, not 403: Bob must not learn that the code exists.
        mvc.perform(delete("/api/links/mine-01").with(as(bob)))
                .andExpect(status().isNotFound());

        assertThat(repository.findByCode("mine-01")).isPresent();
        mvc.perform(get("/mine-01")).andExpect(status().isFound());
    }

    private void create(User owner, String alias) throws Exception {
        mvc.perform(post("/api/links").with(as(owner)).contentType(APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/%s", "alias": "%s"}
                                """.formatted(alias, alias)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }
}