package com.closer.blog.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.closer.blog.auth.config.RefreshTokenProperties;
import com.closer.blog.auth.domain.RefreshToken;
import com.closer.blog.auth.domain.RefreshTokenRepository;
import com.closer.blog.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    private final RefreshTokenProperties properties;

    private final Clock clock;

    /**
     * 로그인할 때 새 토큰 가족을 시작한다.
     */
    @Transactional
    public IssuedRefreshToken issueNewFamily(User user, boolean persistent) {
        return issue(user, UUID.randomUUID(), persistent);
    }

    private IssuedRefreshToken issue(User user, UUID familyId, boolean persistent) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(persistent ? properties.persistentTtl() : properties.sessionTtl());
        String value = newTokenValue();
        refreshTokenRepository.save(RefreshToken.issue(user, familyId, hash(value), persistent, expiresAt, now));
        return new IssuedRefreshToken(value, persistent, expiresAt);
    }

    // 256비트 난수를 URL에 안전한 문자열로
    private static String newTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", ex);
        }
    }

    /**
     * 발급한 토큰. value(원문)는 쿠키로만 내보내고 저장하지 않는다.
     */
    public record IssuedRefreshToken(String value, boolean persistent, Instant expiresAt) {
    }
}
