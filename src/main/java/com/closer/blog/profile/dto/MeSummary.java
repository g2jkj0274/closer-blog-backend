package com.closer.blog.profile.dto;

import java.time.Instant;

import com.closer.blog.profile.repository.ProfileQueryRepository.OwnerStats;
import com.closer.blog.user.domain.User;

/**
 * 홈 화면의 neofetch 줄 (docs/api-spec.md 6절 GET /me/summary).
 *
 * @param todayPostCount Asia/Seoul 기준 오늘 만든 글 수
 */
public record MeSummary(String username, String displayName, String contact, Instant joinedAt, long folderCount,
                        long postCount, long todayPostCount) {

    public static MeSummary of(User me, OwnerStats stats) {
        return new MeSummary(me.getUsername(), me.getDisplayName(), me.getContact(), me.getCreatedAt(),
                stats.folderCount(), stats.readablePostCount(), stats.readablePostCountSince());
    }

}
