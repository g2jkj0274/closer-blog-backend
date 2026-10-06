package com.closer.blog.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    // 로그인 시도 제한 (docs/erd.md 4.1절)
    private static final int MAX_LOGIN_FAILURES = 5;

    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    private static final DateTimeFormatter LOCK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final Clock clock;

    // 없는 계정에도 BCrypt 비교를 한 번 해서, 응답 시간으로 계정 유무가 드러나지 않게 한다.
    // final이 아니므로 @RequiredArgsConstructor의 생성자에 들어가지 않는다
    private volatile String dummyPasswordHash;

    @Transactional
    public User register(String username, String email, String rawPassword, boolean mailOptIn) {
        String normalizedUsername = normalizeUsername(username);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        User user = User.register(normalizedUsername, email, passwordEncoder.encode(rawPassword), mailOptIn,
                clock.instant());
        // 위 검사 뒤에 같은 아이디가 동시에 가입되면 여기서 고유 제약에 걸리고,
        // GlobalExceptionHandler가 409로 바꾼다. 트랜잭션 안에서 바로 드러나도록 flush한다.
        return userRepository.saveAndFlush(user);
    }

    /**
     * 아이디 또는 이메일과 비밀번호로 사용자를 확인한다.
     * 실패 횟수는 예외를 던져도 저장해야 하므로 ApiException에는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User authenticate(String login, String rawPassword) {
        Instant now = clock.instant();
        Optional<User> found = findByLogin(login);
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (user.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "로그인 시도가 많아 잠겼습니다. " + LOCK_TIME_FORMAT.format(user.getLockedUntil()) + " 이후에 다시 시도하세요.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.recordLoginFailure(now, MAX_LOGIN_FAILURES, LOGIN_LOCK_DURATION);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.recordLoginSuccess();
        return user;
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(normalizeUsername(username));
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmailIgnoreCase(email);
    }

    // @가 있으면 이메일, 없으면 아이디 (docs/api-spec.md 5절)
    private Optional<User> findByLogin(String login) {
        return login.contains("@") ? userRepository.findByEmailIgnoreCase(login)
                : userRepository.findByUsername(normalizeUsername(login));
    }

    private String dummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode("dummy-password-for-timing");
            dummyPasswordHash = hash;
        }
        return hash;
    }

    private static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
