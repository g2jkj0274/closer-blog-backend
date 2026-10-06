package com.closer.blog.search.dto;

import java.util.List;

/**
 * 편집기 태그 입력의 자동완성 (docs/api-spec.md 9절 GET /tags).
 */
public record TagSuggestions(List<Item> items) {

    /**
     * @param postCount 보는 사람이 읽을 수 있는 글 수. 0인 태그는 나오지 않는다
     */
    public record Item(String name, long postCount) {
    }

}
