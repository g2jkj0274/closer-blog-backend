package com.closer.blog.search.dto;

import java.util.List;

import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.PostSummary;

/**
 * find의 결과 (docs/api-spec.md 9절 GET /search/names). 폴더와 글 각각 최대 50개, 커서 없음.
 */
public record NameSearchResult(List<FolderHit> folders, List<PostSummary> posts) {

    public record FolderHit(Long id, String name, String displayPath, PostDetail.UserRef owner) {
    }

}
