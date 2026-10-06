package com.closer.blog.profile.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.closer.blog.common.sql.LikePatterns;
import com.closer.blog.common.web.CursorCodec;
import com.closer.blog.post.domain.PostVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사람마다 글 수와 폴더 수를 센다. users, posts, folders를 함께 읽는 조회라
 * profile 패키지가 네이티브 쿼리로 갖는다 (docs/api-spec.md 2절).
 */
@Repository
@RequiredArgsConstructor
public class ProfileQueryRepository {

    private static final String STATS_SQL = """
            SELECT (SELECT count(*) FROM posts p WHERE p.owner_id = :ownerId AND %1$s) AS readable_post_count,
                   (SELECT count(*) FROM posts p WHERE p.owner_id = :ownerId AND %2$s) AS public_post_count,
                   (SELECT count(*) FROM folders f WHERE f.owner_id = :ownerId AND f.parent_id IS NOT NULL)
                       AS folder_count,
                   (SELECT count(*) FROM posts p WHERE p.owner_id = :ownerId AND %1$s AND p.created_at >= :since)
                       AS readable_post_count_since
            """.formatted(PostVisibility.READABLE_SQL, PostVisibility.PUBLIC_SQL);

    // 목록의 글 수는 공개 글 수다. 보는 사람마다 다르면 정렬과 커서가 흔들린다 (docs/api-spec.md 6절 GET /users)
    private static final String USERS_SQL = """
            SELECT u.id, u.username, u.display_name, u.bio, u.created_at, s.post_count, s.folder_count
            FROM users u
            CROSS JOIN LATERAL (
                SELECT (SELECT count(*) FROM posts p WHERE p.owner_id = u.id AND %s) AS post_count,
                       (SELECT count(*) FROM folders f WHERE f.owner_id = u.id AND f.parent_id IS NOT NULL)
                           AS folder_count
            ) s
            WHERE TRUE
            """.formatted(PostVisibility.PUBLIC_SQL);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 한 사람의 글·폴더 수. 폴더는 홈을 빼고 센다.
     *
     * @param viewerId 이 사람이 읽을 수 있는 글을 센다. 본인이면 휴지통을 뺀 전체다
     * @param since readablePostCountSince를 셀 시작 시각
     */
    public OwnerStats statsOf(Long ownerId, Long viewerId, Instant since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("viewerId", viewerId)
                .addValue("since", toOffset(since));
        return jdbcTemplate.queryForObject(STATS_SQL, params, (rs, rowNum) -> new OwnerStats(
                rs.getLong("readable_post_count"), rs.getLong("public_post_count"), rs.getLong("folder_count"),
                rs.getLong("readable_post_count_since")));
    }

    /**
     * 사람 목록. byJoined면 최근 가입순, 아니면 공개 글 많은 순. 같으면 id가 큰 것부터.
     *
     * @param q 아이디나 표시 이름의 일부. 없으면 null
     * @param after 이 커서 뒤부터. 첫 페이지면 null
     */
    public List<UserRow> findUsers(String q, boolean byJoined, CursorCodec.Cursor after, int limit) {
        StringBuilder sql = new StringBuilder(USERS_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        if (q != null) {
            sql.append(" AND (u.username ILIKE :pattern ESCAPE '\\' OR u.display_name ILIKE :pattern ESCAPE '\\')");
            params.addValue("pattern", LikePatterns.contains(q));
        }
        if (after != null && byJoined) {
            sql.append(" AND (u.created_at < :afterJoined OR (u.created_at = :afterJoined AND u.id < :afterId))");
            params.addValue("afterJoined", toOffset(after.sortKeyAsInstant())).addValue("afterId", after.id());
        }
        else if (after != null) {
            sql.append(" AND (s.post_count < :afterCount OR (s.post_count = :afterCount AND u.id < :afterId))");
            params.addValue("afterCount", after.sortKeyAsLong()).addValue("afterId", after.id());
        }
        sql.append(byJoined ? " ORDER BY u.created_at DESC, u.id DESC" : " ORDER BY s.post_count DESC, u.id DESC");
        sql.append(" LIMIT :limit");

        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new UserRow(rs.getLong("id"),
                rs.getString("username"), rs.getString("display_name"), rs.getString("bio"),
                rs.getLong("post_count"), rs.getLong("folder_count"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    // PostgreSQL 드라이버는 Instant를 바로 받지 않는다
    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public record OwnerStats(long readablePostCount, long publicPostCount, long folderCount,
                             long readablePostCountSince) {
    }

    public record UserRow(Long id, String username, String displayName, String bio, long postCount,
                          long folderCount, Instant joinedAt) {
    }

}
