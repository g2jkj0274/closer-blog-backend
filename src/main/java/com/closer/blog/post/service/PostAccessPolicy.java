package com.closer.blog.post.service;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.post.domain.Post;
import org.springframework.stereotype.Component;

/**
 * 글의 읽기·쓰기 권한 (docs/api-spec.md 1.3절).
 * 검사 순서는 "읽을 수 있는가(아니면 404) → 고칠 수 있는가(아니면 403)"다.
 */
@Component
public class PostAccessPolicy {

    public boolean canRead(Post post, Long viewerId) {
        return !post.isDeleted() && (post.isOwnedBy(viewerId) || post.getMode() == Post.MODE_PUBLIC);
    }

    /**
     * 읽을 수 없으면 없는 글과 똑같이 404다. 비공개 글이 있다는 사실이 드러나지 않는다.
     */
    public void checkReadable(Post post, Long viewerId) {
        if (!canRead(post, viewerId)) {
            throw notFound();
        }
    }

    public void checkWritable(Post post, Long viewerId) {
        checkReadable(post, viewerId);
        if (!post.isOwnedBy(viewerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "다른 사람의 글은 고칠 수 없습니다.");
        }
    }

    /**
     * 휴지통의 글은 소유자만 다룬다. 휴지통에 없거나 남의 것이면 404다.
     */
    public void checkInTrashOf(Post post, Long userId) {
        if (!post.isDeleted() || !post.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "휴지통에 없는 글입니다.");
        }
    }

    public ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "없는 글입니다.");
    }

}