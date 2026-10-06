package com.closer.blog.auth.controller;

import com.closer.blog.auth.config.RefreshTokenProperties;
import com.closer.blog.auth.service.RefreshTokenService.IssuedRefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키 (docs/api-spec.md 1.2절).
 * Path를 /api/v1/auth로 좁혀 다른 API 요청에는 쿠키가 실리지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    private static final String COOKIE_PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    public ResponseCookie create(IssuedRefreshToken token) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, token.value())
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH);
        // 로그인 유지면 30일 쿠키, 아니면 Max-Age 없는 세션 쿠키(브라우저를 닫으면 사라짐)
        if (token.persistent()) {
            builder.maxAge(properties.persistentTtl());
        }
        return builder.build();
    }

}