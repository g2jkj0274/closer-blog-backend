package com.closer.blog.common.security;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(JWT)을 발급한다. 검증은 Spring Security의 리소스 서버가 {@code JwtDecoder}로 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtEncoder jwtEncoder;

    private final JwtProperties properties;

    private final Clock clock;

    public AccessToken issue(long userId, String username) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

}