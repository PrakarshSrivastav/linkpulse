package com.example.linkpulse.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "linkpulse.rate-limit.enabled", havingValue = "true")
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    public RateLimitConfig(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limiter, props))
                .addPathPatterns("/api/auth/login", "/api/auth/register", "/api/links");
    }
}
