package com.example.linkpulse.link;

import java.time.Instant;

public record LinkSummary(String code, String shortUrl, String longUrl, Instant createdAt, Instant expiresAt) {}