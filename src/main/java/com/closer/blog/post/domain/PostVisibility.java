package com.closer.blog.post.domain;

/**
 * 글 읽기 권한을 쿼리 조건으로 쓴 것 (docs/erd.md 5.1절). 글을 보여 주거나 세는 모든 쿼리가 이 조건을 쓴다.
 * 별칭은 p, 보는 사람 파라미터는 :viewerId로 맞춘다. 자바 코드의 같은 규칙은 PostAccessPolicy.canRead다.
 */
public final class PostVisibility {

    // JPQL용. @Query 문자열에 이어 붙인다
    public static final String READABLE_JPQL = "p.deletedAt is null and (p.owner.id = :viewerId or p.mode = 644)";

    // 네이티브 쿼리용. search, profile 패키지가 쓴다
    public static final String READABLE_SQL = "p.deleted_at IS NULL AND (p.owner_id = :viewerId OR p.mode = 644)";

    // 보는 사람과 상관없는 공개 글. 사람 목록의 글 수처럼 누가 봐도 같아야 하는 값에 쓴다
    public static final String PUBLIC_SQL = "p.deleted_at IS NULL AND p.mode = 644";

    private PostVisibility() {
    }

}
