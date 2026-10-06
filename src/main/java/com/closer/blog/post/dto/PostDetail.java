package com.closer.blog.post.dto;

import java.time.Instant;
import java.util.List;

import com.closer.blog.common.web.DisplayPath;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.post.domain.Post;
import com.closer.blog.user.domain.User;

/**
 * 글 하나 (docs/api-spec.md 4절 PostDetail). 지연 로딩 연관을 읽으므로 트랜잭션 안에서 만든다.
 */
public record PostDetail(Long id, String fileName, String path, String displayPath, String title, List<String> tags,
                         int mode, UserRef owner, Instant createdAt, Instant updatedAt, String content, FolderRef folder,
                         int charCount, int readingMinutes, int version, boolean editable) {

    // 500자에 1분 (docs/erd.md 4.4절)
    private static final int CHARS_PER_MINUTE = 500;

    public static PostDetail of(Post post, Long viewerId) {
        User owner = post.getOwner();
        Folder folder = post.getFolder();
        boolean mine = post.isOwnedBy(viewerId);
        return new PostDetail(post.getId(), post.getFileName(), post.getPath(),
                DisplayPath.of(mine, owner.getUsername(), post.getPath()), post.getTitle(), post.getTagNames(),
                post.getMode(), new UserRef(owner.getUsername(), owner.getDisplayName()), post.getCreatedAt(),
                post.getUpdatedAt(), post.getContent(),
                new FolderRef(folder.getId(), folder.getPath(),
                        DisplayPath.of(mine, owner.getUsername(), folder.getPath())),
                post.getCharCount(), (post.getCharCount() + CHARS_PER_MINUTE - 1) / CHARS_PER_MINUTE,
                post.getVersion(), mine);
    }

    public record UserRef(String username, String displayName) {
    }

    public record FolderRef(Long id, String path, String displayPath) {
    }

}