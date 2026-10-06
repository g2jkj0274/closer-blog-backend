package com.closer.blog.profile.dto;

import java.time.Instant;

import com.closer.blog.profile.repository.ProfileQueryRepository.OwnerStats;
import com.closer.blog.user.domain.User;

/**
 * 다른 사람 블로그(화면 12)의 프로필 패널 (docs/api-spec.md 6절 GET /users/{username}).
 * 이메일과 연락처는 넣지 않는다.
 *
 * @param postCount 보는 사람이 읽을 수 있는 글 수. 남이 보면 공개 글, 본인이 보면 전체
 */
public record UserProfile(String username, String displayName, String bio, Instant joinedAt, long postCount,
                          long folderCount, boolean isMe) {

    public static UserProfile of(User user, OwnerStats stats, boolean isMe) {
        return new UserProfile(user.getUsername(), user.getDisplayName(), user.getBio(), user.getCreatedAt(),
                stats.readablePostCount(), stats.folderCount(), isMe);
    }

}
