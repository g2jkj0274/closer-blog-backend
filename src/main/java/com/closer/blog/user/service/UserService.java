package com.closer.blog.user.service;

import java.time.Clock;
import java.util.Locale;

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

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final Clock clock;

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

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(normalizeUsername(username));
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmailIgnoreCase(email);
    }

    private static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
