package com.closer.blog.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 리프레시 토큰 수명과 쿠키 설정 (docs/api-spec.md 1.2절).
 */
@ConfigurationProperties("app.auth.refresh-token")
public record RefreshTokenProperties(Duration persistentTtl, Duration sessionTtl, Boolean cookieSecure) {

    public RefreshTokenProperties {
        if (persistentTtl == null) {
            persistentTtl = Duration.ofDays(30);
        }
        if (sessionTtl == null) {
            sessionTtl = Duration.ofHours(24);
        }
        if (cookieSecure == null) {
            cookieSecure = true;
        }
    }

}