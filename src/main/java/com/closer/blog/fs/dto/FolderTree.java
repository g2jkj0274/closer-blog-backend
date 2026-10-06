package com.closer.blog.fs.dto;

import java.time.Instant;
import java.util.List;

/**
 * 왼쪽 트리 (docs/api-spec.md 7절 GET /users/{username}/tree). 폴더만 담고 글은 개수로 준다.
 */
public record FolderTree(Node root) {

    /**
     * FolderSummary에 children을 더한 것. 자식은 이름순이다.
     */
    public record Node(Long id, String name, String path, String displayPath, long postCount, long folderCount,
                       Instant createdAt, List<Node> children) {
    }

}
