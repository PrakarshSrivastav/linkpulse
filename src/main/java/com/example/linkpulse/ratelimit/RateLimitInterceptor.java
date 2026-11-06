package com.example.linkpulse.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

// Runs after the security filter chain (so the JWT is already verified) and before the controller
// (so a refused login never reaches BCrypt).
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    public RateLimitInterceptor(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        RateLimiter.Decision decision = switch (path) {
            case "/api/auth/login" -> limiter.tryAcquire("login:" + clientIp(request), props.login());
            case "/api/auth/register" -> limiter.tryAcquire("register:" + clientIp(request), props.register());
            case "/api/links" -> limiter.tryAcquire("create:" + userId(), props.createLink());
            default -> RateLimiter.Decision.ALLOW;
        };
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }
        return true;
    }

    // Behind a proxy, getRemoteAddr() is the proxy's address unless server.forward-headers-strategy
    // is set. Only enable it when the app is reachable solely through that proxy, otherwise a
    // client can send its own X-Forwarded-For and pick a fresh bucket on every request.
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    // /api/links requires authentication, so the verified JWT subject (the user id) is present here.
    private static String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}
