package com.closer.blog.search.dto;

import java.util.List;

import com.closer.blog.post.dto.PostSummary;

/**
 * 태그별 글 목록 (docs/api-spec.md 9절 GET /tags/{name}/posts). 태그가 없어도 404가 아니라 빈 목록이다.
 *
 * @param tag 저장된 표기. 태그가 없으면 요청한 이름 그대로
 */
public record TagPosts(TagRef tag, List<PostSummary> items, String nextCursor) {

    public record TagRef(String name) {
    }

}
