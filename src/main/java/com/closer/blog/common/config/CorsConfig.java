package com.closer.blog.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));          // 만들기 API의 201 응답 Location 헤더를 프론트 JS가 읽을 수 있게 함
        // 리프레시 토큰 쿠키를 주고받는다 (docs/api-spec.md 1.2절)
        config.setAllowCredentials(true);                           // 리프레시 토큰 쿠키를 다른 출처(프론트)와 주고 받으려면 필요
        config.setMaxAge(Duration.ofHours(1));                      // 브라우저가 사전 요청 결과를 1시간 동안 기억해 요청 수를 줄인다

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
