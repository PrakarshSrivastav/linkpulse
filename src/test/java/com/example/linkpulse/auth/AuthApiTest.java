package com.example.linkpulse.auth;

import com.example.linkpulse.user.UserRepository;
import com.example.linkpulse.link.LinkRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    LinkRepository links;

    @BeforeEach
    void clean() {
        links.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void registerLoginAndCallProtectedEndpoint() throws Exception {
        register("Me@Example.com", "correct-horse-battery")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("me@example.com"));

        String token = login("me@example.com", "correct-horse-battery");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register("me@example.com", "correct-horse-battery").andExpect(status().isCreated());
        register("ME@example.com", "another-password").andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        register("me@example.com", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void passwordOver72BytesIsRejectedEvenWhenUnder72Characters() throws Exception {
        // 40 x "é" = 40 characters but 80 UTF-8 bytes: passes @Size, would overflow BCrypt.
        register("me@example.com", "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passwordWithinBcryptLimit").exists());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        register("me@example.com", "correct-horse-battery").andExpect(status().isCreated());

        loginRequest("me@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        loginRequest("nobody@example.com", "correct-horse-battery")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointRejectsMissingAndTamperedTokens() throws Exception {
        register("me@example.com", "correct-horse-battery").andExpect(status().isCreated());
        String token = login("me@example.com", "correct-horse-battery");

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String login(String email, String password) throws Exception {
        String body = loginRequest(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}