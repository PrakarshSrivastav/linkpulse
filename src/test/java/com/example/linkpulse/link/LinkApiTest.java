package com.example.linkpulse.link;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LinkApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LinkRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsLinkWithRandomCode() throws Exception {
        mvc.perform(post("/api/links").contentType(APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.shortUrl").value(startsWith("http://localhost:8080/")));
    }

    @Test
    void rejectsNonHttpUrl() throws Exception {
        mvc.perform(post("/api/links").contentType(APPLICATION_JSON)
                        .content("""
                                {"url": "javascript:alert(1)"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());
    }

    @Test
    void rejectsInvalidAlias() throws Exception {
        mvc.perform(post("/api/links").contentType(APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "alias": "a b"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.alias").exists());
    }

    @Test
    void takenAliasReturns409() throws Exception {
        String body = """
                {"url": "https://example.com", "alias": "my-alias"}
                """;
        mvc.perform(post("/api/links").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/links").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void redirectsWith302() throws Exception {
        repository.saveAndFlush(new Link("abc1234", "https://example.com/page", null));

        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mvc.perform(get("/nope123")).andExpect(status().isNotFound());
    }

    @Test
    void expiredLinkReturns410() throws Exception {
        repository.saveAndFlush(new Link("old1234", "https://example.com", Instant.now().minusSeconds(60)));

        mvc.perform(get("/old1234")).andExpect(status().isGone());
    }
}