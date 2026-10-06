package com.closer.blog.profile.dto;

import java.time.Instant;

import com.closer.blog.profile.repository.ProfileQueryRepository.OwnerStats;
import com.closer.blog.user.domain.User;

/**
 * 내 정보 (docs/api-spec.md 6절 GET /me). 이메일과 연락처는 본인에게만 준다.
 *
 * @param postCount 휴지통을 뺀 내 글 전체
 * @param publicPostCount 그중 644
 * @param folderCount 홈을 뺀 내 폴더
 */
public record MeResponse(Long id, String username, String displayName, String email, String bio, String contact,
                         Instant joinedAt, long postCount, long publicPostCount, long folderCount) {

    public static MeResponse of(User me, OwnerStats stats) {
        return new MeResponse(me.getId(), me.getUsername(), me.getDisplayName(), me.getEmail(), me.getBio(),
                me.getContact(), me.getCreatedAt(), stats.readablePostCount(), stats.publicPostCount(),
                stats.folderCount());
    }

}
