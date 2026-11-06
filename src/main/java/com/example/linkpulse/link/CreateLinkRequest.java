package com.example.linkpulse.link;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.time.Instant;

public record CreateLinkRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "must be an http or https URL")
        String url,

        @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$",
                message = "must be 4-32 characters: letters, digits, _ or -")
        String alias,

        @Future
        Instant expiresAt
) {
    // The regex lets through characters like | or { that URI.create rejects. Without this check
    // the link would be saved and then every redirect to it would fail with a 500.
    @AssertTrue(message = "must be a valid URL")
    boolean isUrlParsable() {
        if (url == null) {
            return true;
        }
        try {
            URI.create(url);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
