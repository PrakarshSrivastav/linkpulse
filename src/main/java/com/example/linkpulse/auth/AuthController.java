package com.example.linkpulse.auth;

import com.example.linkpulse.user.User;
import com.example.linkpulse.web.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Register, log in and inspect the current user")
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(user.getId(), user.getEmail()));
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return new TokenResponse(token, "Bearer", tokenService.ttl().toSeconds());
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new UserResponse(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"));
    }
}