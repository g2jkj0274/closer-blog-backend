package com.closer.blog.search.repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import com.closer.blog.common.sql.LikePatterns;
import com.closer.blog.common.web.CursorCodec;
import com.closer.blog.post.domain.PostVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 검색 쿼리. ILIKE는 pg_trgm GIN 인덱스(ix_posts_content_trgm, ix_posts_file_name_trgm, ix_folders_path_trgm)로
 * 거른다 (docs/erd.md 5.3절). 글은 id만 찾고, 글 내용은 post 패키지가 읽는다.
 * 모든 글 조건에 PostVisibility를 쓴다. mineOnly면 내 글만 본다.
 */
@Repository
@RequiredArgsConstructor
public class SearchQueryRepository {

    private static final String READABLE = " AND " + PostVisibility.READABLE_SQL;

    private static final String MINE = " AND p.owner_id = :viewerId";

    // 최근 수정순 커서 (updated_at, id)
    private static final String AFTER_UPDATED =
            " AND (p.updated_at < :afterUpdated OR (p.updated_at = :afterUpdated AND p.id < :afterId))";

    private static final String ORDER_BY_UPDATED = " ORDER BY p.updated_at DESC, p.id DESC LIMIT :limit";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * grep. 본문에 검색어가 들어 있는 글 id, 최근 수정순.
     */
    public List<Long> findPostIdsByContent(String q, Long viewerId, boolean mineOnly, CursorCodec.Cursor after,
                                           int limit) {
        StringBuilder sql = new StringBuilder("SELECT p.id FROM posts p WHERE p.content ILIKE :pattern ESCAPE '\\'");
        MapSqlParameterSource params = params(viewerId).addValue("pattern", LikePatterns.contains(q));
        appendFilters(sql, params, mineOnly, after);
        sql.append(ORDER_BY_UPDATED);
        params.addValue("limit", limit);
        return jdbcTemplate.queryForList(sql.toString(), params, Long.class);
    }

    /**
     * grep의 fileCount. 일치한 글 수.
     */
    public long countPostsByContent(String q, Long viewerId, boolean mineOnly) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) FROM posts p WHERE p.content ILIKE :pattern ESCAPE '\\'");
        MapSqlParameterSource params = params(viewerId).addValue("pattern", LikePatterns.contains(q));
        appendFilters(sql, params, mineOnly, null);
        return jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
    }

    /**
     * find의 글. 파일 이름이나 폴더 경로에 검색어가 들어 있는 글 id.
     * 파일 이름이 검색어로 시작하는 것을 먼저 놓고, 그다음 최근 수정순.
     */
    public List<Long> findPostIdsByName(String q, Long viewerId, boolean mineOnly, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id FROM posts p JOIN folders f ON f.id = p.folder_id
                WHERE (p.file_name ILIKE :pattern ESCAPE '\\' OR f.path ILIKE :pattern ESCAPE '\\')""");
        MapSqlParameterSource params = params(viewerId)
                .addValue("pattern", LikePatterns.contains(q))
                .addValue("prefix", LikePatterns.startsWith(q))
                .addValue("limit", limit);
        appendFilters(sql, params, mineOnly, null);
        sql.append(" ORDER BY (p.file_name ILIKE :prefix ESCAPE '\\') DESC, p.updated_at DESC, p.id DESC LIMIT :limit");
        return jdbcTemplate.queryForList(sql.toString(), params, Long.class);
    }

    /**
     * find의 폴더. 경로에 검색어가 들어 있는 폴더. 폴더는 누구나 볼 수 있다. 홈은 빼고 찾는다.
     * 이름이 검색어로 시작하는 것을 먼저 놓고, 그다음 최근에 만든 순.
     */
    public List<FolderRow> findFoldersByName(String q, Long viewerId, boolean mineOnly, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT f.id, f.name, f.path, f.owner_id, u.username, u.display_name
                FROM folders f JOIN users u ON u.id = f.owner_id
                WHERE f.parent_id IS NOT NULL AND f.path ILIKE :pattern ESCAPE '\\'""");
        if (mineOnly) {
            sql.append(" AND f.owner_id = :viewerId");
        }
        sql.append(" ORDER BY (f.name ILIKE :prefix ESCAPE '\\') DESC, f.created_at DESC, f.id DESC LIMIT :limit");
        MapSqlParameterSource params = params(viewerId)
                .addValue("pattern", LikePatterns.contains(q))
                .addValue("prefix", LikePatterns.startsWith(q))
                .addValue("limit", limit);
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new FolderRow(rs.getLong("id"),
                rs.getString("name"), rs.getString("path"), rs.getLong("owner_id"), rs.getString("username"),
                rs.getString("display_name")));
    }

    /**
     * 태그 자동완성. 이름의 앞부분 일치, 대소문자 무시. 읽을 수 있는 글이 없는 태그는 빠지고, 글 많은 순.
     * lower(name) LIKE '검색어%'는 ix_tags_name_prefix(text_pattern_ops)를 쓴다.
     */
    public List<TagRow> suggestTags(String q, Long viewerId, int limit) {
        String sql = """
                SELECT t.name, count(*) AS post_count
                FROM tags t
                JOIN post_tags pt ON pt.tag_id = t.id
                JOIN posts p ON p.id = pt.post_id
                WHERE lower(t.name) LIKE :prefix ESCAPE '\\' AND %s
                GROUP BY t.id, t.name
                ORDER BY post_count DESC, t.name
                LIMIT :limit
                """.formatted(PostVisibility.READABLE_SQL);
        MapSqlParameterSource params = params(viewerId)
                .addValue("prefix", LikePatterns.startsWith(q.toLowerCase(Locale.ROOT)))
                .addValue("limit", limit);
        return jdbcTemplate.query(sql, params,
                (rs, rowNum) -> new TagRow(rs.getString("name"), rs.getLong("post_count")));
    }

    /**
     * 태그별 글 id, 최근 수정순.
     */
    public List<Long> findPostIdsByTag(Long tagId, Long viewerId, boolean mineOnly, CursorCodec.Cursor after,
                                       int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id FROM posts p JOIN post_tags pt ON pt.post_id = p.id WHERE pt.tag_id = :tagId");
        MapSqlParameterSource params = params(viewerId).addValue("tagId", tagId);
        appendFilters(sql, params, mineOnly, after);
        sql.append(ORDER_BY_UPDATED);
        params.addValue("limit", limit);
        return jdbcTemplate.queryForList(sql.toString(), params, Long.class);
    }

    private static MapSqlParameterSource params(Long viewerId) {
        return new MapSqlParameterSource().addValue("viewerId", viewerId);
    }

    // 읽기 권한, 내 글만, 커서 조건을 붙인다
    private static void appendFilters(StringBuilder sql, MapSqlParameterSource params, boolean mineOnly,
                                      CursorCodec.Cursor after) {
        sql.append(READABLE);
        if (mineOnly) {
            sql.append(MINE);
        }
        if (after != null) {
            sql.append(AFTER_UPDATED);
            // PostgreSQL 드라이버는 Instant를 바로 받지 않는다
            params.addValue("afterUpdated", after.sortKeyAsInstant().atOffset(ZoneOffset.UTC))
                    .addValue("afterId", after.id());
        }
    }

    public record FolderRow(Long id, String name, String path, Long ownerId, String ownerUsername,
                            String ownerDisplayName) {
    }

    public record TagRow(String name, long postCount) {
    }

}
