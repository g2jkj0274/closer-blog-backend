package com.closer.blog.auth.service;

import com.closer.blog.auth.dto.LoginRequest;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.service.RefreshTokenService.IssuedRefreshToken;
import com.closer.blog.common.security.JwtProvider;
import com.closer.blog.common.security.JwtProvider.AccessToken;
import com.closer.blog.folder.service.FolderService;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;

    private final FolderService folderService;

    private final RefreshTokenService refreshTokenService;

    private final JwtProvider jwtProvider;

    /**
     * 사용자와 홈 폴더를 한 트랜잭션에서 만든다. 둘 중 하나라도 실패하면 둘 다 생기지 않는다.
     */
    @Transactional
    public User signup(SignupRequest request) {
        User user = userService.register(request.username(), request.email(), request.password(),
                Boolean.TRUE.equals(request.mailOptIn()));
        folderService.createHome(user);
        return user;
    }

    /**
     * 트랜잭션을 걸지 않는다. 비밀번호 확인(실패 횟수 기록 포함)과 토큰 발급이 각자의 트랜잭션에서 끝난다.
     */
    public LoginResult login(LoginRequest request) {
        User user = userService.authenticate(request.login(), request.password());
        AccessToken accessToken = jwtProvider.issue(user.getId(), user.getUsername());
        IssuedRefreshToken refreshToken = refreshTokenService.issueNewFamily(user,
                Boolean.TRUE.equals(request.keepLoggedIn()));
        return new LoginResult(user, accessToken, refreshToken);
    }

    public record LoginResult(User user, AccessToken accessToken, IssuedRefreshToken refreshToken) {
    }
}
