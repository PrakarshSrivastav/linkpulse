package com.example.linkpulse.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254)
        String email,

        @NotBlank @Size(min = 8, max = 72)
        String password
) {
    // BCrypt only uses the first 72 BYTES. @Size counts characters, so "é" or an emoji
    // (2-4 bytes each) could pass @Size and then make BCrypt throw (500).
    @AssertTrue(message = "must be at most 72 bytes")
    boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
