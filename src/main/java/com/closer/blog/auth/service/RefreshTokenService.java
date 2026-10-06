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
import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
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

    /**
     * 받은 토큰을 새 토큰으로 바꾼다 (docs/erd.md 4.2절 "회전과 재사용 감지").
     * 재사용을 감지하면 가족을 지운 뒤 예외를 던지므로, ApiException에는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findWithUserByTokenHash(hash(rawToken))
                .filter(found -> found.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        User user = token.getUser();

        // 1. 현재 토큰이면 회전한다
        if (token.getRotatedAt() == null && refreshTokenRepository.markRotated(token.getId(), now) == 1) {
            return new Rotation(user, issue(user, token.getFamilyId(), token.isPersistent()));
        }

        // 2. 이미 회전된 토큰. 회전 표시에 실패했다면 다른 요청이 방금 회전한 것이다
        Instant rotatedAt = (token.getRotatedAt() != null) ? token.getRotatedAt() : now;
        if (!now.isAfter(rotatedAt.plus(properties.rotationGrace()))) {
            // 다른 탭이 방금 회전했다. 새 쿠키는 그 응답으로 이미 브라우저에 있다
            return new Rotation(user, null);
        }

        // 3. 유예 시간이 지난 옛 토큰이 다시 왔다. 탈취로 보고 가족을 모두 폐기한다
        refreshTokenRepository.deleteByFamilyId(token.getFamilyId());
        throw new ApiException(ErrorCode.UNAUTHORIZED, "보안을 위해 로그아웃되었습니다. 다시 로그인하세요.");
    }

    /**
     * 로그아웃. 받은 토큰의 가족을 모두 지운다. 없는 토큰이어도 조용히 끝난다.
     */
    @Transactional
    public void revokeFamily(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.deleteByFamilyId(token.getFamilyId()));
    }

    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteExpired(clock.instant());
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

    /**
     * 회전 결과. 유예 시간 안의 재요청이면 newToken이 null이다 (쿠키를 다시 주지 않는다).
     */
    public record Rotation(User user, IssuedRefreshToken newToken) {
    }
}
