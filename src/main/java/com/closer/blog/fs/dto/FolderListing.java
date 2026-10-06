package com.closer.blog.fs.dto;

import java.time.Instant;
import java.util.List;

import com.closer.blog.common.web.CursorPage;
import com.closer.blog.folder.dto.FolderSummary;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.PostSummary;

/**
 * 폴더 안 목록 (docs/api-spec.md 7절 GET /folders/{id}). 홈이면 parent가 null이다.
 */
public record FolderListing(FolderInfo folder, ParentRef parent, List<FolderSummary> folders,
                            CursorPage<PostSummary> posts) {

    /**
     * 지금 보고 있는 폴더. FolderSummary에 소유자와 editable(내 폴더인가)을 더한다.
     */
    public record FolderInfo(Long id, String name, String path, String displayPath, long postCount, long folderCount,
                             Instant createdAt, PostDetail.UserRef owner, boolean editable) {
    }

    public record ParentRef(Long id, String displayPath) {
    }

}
