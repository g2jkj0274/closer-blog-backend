package com.closer.blog.auth.dto;

import java.time.Instant;

import com.closer.blog.user.domain.User;

public record SignupResponse(Long id, String username, String displayName, Instant joinedAt) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getCreatedAt());
    }

}