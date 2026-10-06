package com.closer.blog.user.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private String username;

    private String email;

    private String passwordHash;

    private String displayName;

    private String bio;

    private String contact;

    @Column(updatable = false)
    private Instant termsAgreedAt;

    private boolean mailOptIn;

    private int failedLoginCount;

    private Instant lockedUntil;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private User(String username, String email, String passwordHash, boolean mailOptIn, Instant now) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = username;
        this.bio = "";
        this.contact = "";
        this.termsAgreedAt = now;
        this.mailOptIn = mailOptIn;
        this.failedLoginCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 회원가입. 약관 동의는 호출 전에 확인한다. 표시 이름은 아이디로 시작한다 (기능 명세서 8절 2번).
     */
    public static User register(String username, String email, String passwordHash, boolean mailOptIn,
                                Instant now) {
        return new User(username, email, passwordHash, mailOptIn, now);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 비밀번호가 틀렸을 때. maxFailures번째 실패에서 잠그고 횟수를 0으로 되돌린다 (docs/erd.md 4.1절).
     */
    public void recordLoginFailure(Instant now, int maxFailures, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
        }
    }

    public void recordLoginSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }
}
