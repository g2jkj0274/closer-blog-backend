package com.closer.blog.auth.domain;

import java.time.Instant;
import java.util.UUID;

import com.closer.blog.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리프레시 토큰. 원문은 쿠키에만 있고 여기에는 SHA-256 해시만 둔다 (docs/erd.md 4.2절).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    // 로그인 한 번에 하나. 회전해도 유지된다
    @Column(updatable = false)
    private UUID familyId;

    @Column(updatable = false)
    private String tokenHash;

    @Column(updatable = false)
    private boolean persistent;

    @Column(updatable = false)
    private Instant expiresAt;

    // NULL이면 가족의 현재 토큰. 회전(/auth/refresh)은 다음 작업에서 만든다
    private Instant rotatedAt;

    @Column(updatable = false)
    private Instant createdAt;

    private RefreshToken(User user, UUID familyId, String tokenHash, boolean persistent, Instant expiresAt,
                         Instant createdAt) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.persistent = persistent;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static RefreshToken issue(User user, UUID familyId, String tokenHash, boolean persistent,
                                     Instant expiresAt, Instant now) {
        return new RefreshToken(user, familyId, tokenHash, persistent, expiresAt, now);
    }
}
