package com.example.linkpulse.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}