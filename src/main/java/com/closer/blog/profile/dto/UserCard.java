package com.closer.blog.profile.dto;

import java.time.Instant;

import com.closer.blog.profile.repository.ProfileQueryRepository.UserRow;

/**
 * 사람 목록 한 줄 (docs/api-spec.md 6절 GET /users).
 *
 * @param postCount 공개 글 수. 내 카드에도 비공개 글은 세지 않는다
 */
public record UserCard(String username, String displayName, String bio, long postCount, long folderCount,
                       Instant joinedAt) {

    public static UserCard of(UserRow row) {
        return new UserCard(row.username(), row.displayName(), row.bio(), row.postCount(), row.folderCount(),
                row.joinedAt());
    }

}
