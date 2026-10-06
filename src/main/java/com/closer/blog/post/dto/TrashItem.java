package com.closer.blog.post.dto;

import java.time.Instant;

import com.closer.blog.post.domain.Post;

/**
 * 휴지통 목록 한 줄 (docs/api-spec.md 8절 GET /trash). 내 휴지통만 보므로 경로가 늘 ~로 시작한다.
 */
public record TrashItem(Long id, String fileName, String title, String originalDisplayPath, Instant deletedAt,
                        Instant purgeAt) {

    public static TrashItem of(Post post) {
        return new TrashItem(post.getId(), post.getFileName(), post.getTitle(), "~/" + post.getPath(),
                post.getDeletedAt(), post.getDeletedAt().plus(Post.TRASH_RETENTION));
    }

}
