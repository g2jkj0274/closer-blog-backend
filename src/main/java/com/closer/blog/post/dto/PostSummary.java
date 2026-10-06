package com.closer.blog.post.dto;

import java.time.Instant;
import java.util.List;

import com.closer.blog.common.web.DisplayPath;
import com.closer.blog.post.domain.Post;
import com.closer.blog.user.domain.User;

/**
 * 목록에 쓰는 글 (docs/api-spec.md 4절 PostSummary). 지연 로딩 연관을 읽으므로 트랜잭션 안에서 만든다.
 */
public record PostSummary(Long id, String fileName, String path, String displayPath, String title, List<String> tags,
                          int mode, PostDetail.UserRef owner, Instant createdAt, Instant updatedAt) {

    public static PostSummary of(Post post, Long viewerId) {
        User owner = post.getOwner();
        return new PostSummary(post.getId(), post.getFileName(), post.getPath(),
                DisplayPath.of(post.isOwnedBy(viewerId), owner.getUsername(), post.getPath()), post.getTitle(),
                post.getTagNames(), post.getMode(), new PostDetail.UserRef(owner.getUsername(), owner.getDisplayName()),
                post.getCreatedAt(), post.getUpdatedAt());
    }

}
