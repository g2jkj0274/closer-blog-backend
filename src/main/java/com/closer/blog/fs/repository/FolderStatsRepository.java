package com.closer.blog.fs.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.closer.blog.post.domain.PostVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 폴더마다 글 수, 하위 폴더 수, 가장 최근에 고친 글의 시각을 한 번에 센다.
 * folders와 posts를 함께 읽는 조회라 fs 패키지가 네이티브 쿼리로 갖는다 (docs/api-spec.md 2절).
 */
@Repository
@RequiredArgsConstructor
public class FolderStatsRepository {

    // 글은 보는 사람이 읽을 수 있는 것만 센다 (docs/api-spec.md 1.3절 "개수도 읽을 수 있는 글만 센다")
    private static final String STATS_SQL = """
            SELECT f.id,
                   (SELECT count(*) FROM posts p WHERE p.folder_id = f.id AND %1$s) AS post_count,
                   (SELECT count(*) FROM folders c WHERE c.parent_id = f.id) AS folder_count,
                   (SELECT max(p.updated_at) FROM posts p WHERE p.folder_id = f.id AND %1$s) AS last_updated_at
            FROM folders f
            WHERE f.id IN (:folderIds)
            """.formatted(PostVisibility.READABLE_SQL);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<Long, FolderStats> statsOf(Collection<Long> folderIds, Long viewerId) {
        Map<Long, FolderStats> stats = new HashMap<>();
        if (folderIds.isEmpty()) {
            return stats;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("folderIds", folderIds)
                .addValue("viewerId", viewerId);
        jdbcTemplate.query(STATS_SQL, params, rs -> {
            OffsetDateTime lastUpdatedAt = rs.getObject("last_updated_at", OffsetDateTime.class);
            stats.put(rs.getLong("id"), new FolderStats(rs.getLong("post_count"), rs.getLong("folder_count"),
                    (lastUpdatedAt == null) ? null : lastUpdatedAt.toInstant()));
        });
        return stats;
    }

    /**
     * @param lastUpdatedAt 읽을 수 있는 글이 없으면 null
     */
    public record FolderStats(long postCount, long folderCount, Instant lastUpdatedAt) {
    }

}
