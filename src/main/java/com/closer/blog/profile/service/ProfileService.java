package com.closer.blog.profile.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.closer.blog.common.web.CursorCodec;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.profile.dto.MeResponse;
import com.closer.blog.profile.dto.MeSummary;
import com.closer.blog.profile.dto.UpdateProfileRequest;
import com.closer.blog.profile.dto.UserCard;
import com.closer.blog.profile.dto.UserProfile;
import com.closer.blog.profile.repository.ProfileQueryRepository;
import com.closer.blog.profile.repository.ProfileQueryRepository.OwnerStats;
import com.closer.blog.profile.repository.ProfileQueryRepository.UserRow;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보와 사람 조회. 프로필 수정만 쓰기이고 user 패키지의 UserService에 맡긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    // "오늘"의 기준 (docs/api-spec.md 6절 GET /me/summary)
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserService userService;

    private final ProfileQueryRepository queryRepository;

    private final Clock clock;

    public MeResponse me(Long userId) {
        User me = userService.getById(userId);
        return MeResponse.of(me, statsOf(me, userId));
    }

    @Transactional
    public MeResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User me = userService.updateProfile(userId, request.displayName(), request.bio(), request.contact());
        return MeResponse.of(me, statsOf(me, userId));
    }

    public MeSummary summary(Long userId) {
        User me = userService.getById(userId);
        return MeSummary.of(me, statsOf(me, userId));
    }

    public UserProfile profile(Long viewerId, String username) {
        User user = userService.getByUsername(username);
        return UserProfile.of(user, statsOf(user, viewerId), user.getId().equals(viewerId));
    }

    /**
     * 사람 목록. byJoined면 최근 가입순, 아니면 공개 글 많은 순.
     */
    public CursorPage<UserCard> list(String q, boolean byJoined, String cursor, int size) {
        CursorCodec.Cursor after = (cursor == null) ? null : CursorCodec.decode(cursor);
        List<UserRow> rows = queryRepository.findUsers(q, byJoined, after, size + 1);
        return CursorPage.of(rows, size, UserCard::of, row -> CursorCodec.encode(
                byJoined ? row.joinedAt().toString() : String.valueOf(row.postCount()), row.id()));
    }

    private OwnerStats statsOf(User owner, Long viewerId) {
        Instant startOfToday = LocalDate.now(clock.withZone(SEOUL)).atStartOfDay(SEOUL).toInstant();
        return queryRepository.statsOf(owner.getId(), viewerId, startOfToday);
    }

}
