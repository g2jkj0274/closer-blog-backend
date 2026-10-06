package com.closer.blog.auth.dto;

import com.closer.blog.common.security.JwtProvider.AccessToken;
import com.closer.blog.user.domain.User;

public record LoginResponse(String accessToken, long expiresIn, UserInfo user) {

    public static LoginResponse of(AccessToken accessToken, User user) {
        return new LoginResponse(accessToken.value(), accessToken.expiresInSeconds(),
                new UserInfo(user.getId(), user.getUsername(), user.getDisplayName()));
    }

    public record UserInfo(Long id, String username, String displayName) {
    }

}