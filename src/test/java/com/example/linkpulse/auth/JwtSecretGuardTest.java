package com.example.linkpulse.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain unit test: no Spring context, just the bean method that turns JWT_SECRET into a key.
class JwtSecretGuardTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void rejectsSecretsShorterThan32Bytes() {
        assertThatThrownBy(() -> config.jwtSigningKey("only-31-bytes-long-xxxxxxxxxxxx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsTheSecretThatWasOnceCommittedToGit() {
        assertThatThrownBy(() -> config.jwtSigningKey("dev-only-secret-change-me-at-least-32-bytes!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("git history");
    }

    @Test
    void acceptsALongRandomSecret() {
        assertThat(config.jwtSigningKey("a-perfectly-fine-test-secret-of-48-bytes-0123456").getAlgorithm())
                .isEqualTo("HmacSHA256");
    }
}
