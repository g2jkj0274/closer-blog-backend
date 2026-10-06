package com.closer.blog.fs.dto;

import java.util.List;

import com.closer.blog.common.web.CursorPage;
import com.closer.blog.folder.dto.FolderSummary;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.PostSummary;

/**
 * GET /fs의 응답 (docs/api-spec.md 7절). type이 folder면 GET /folders/{id}와 같은 본문, post면 PostDetail이다.
 */
public sealed interface FsResponse {

    record FolderResult(String type, FolderListing.FolderInfo folder, FolderListing.ParentRef parent,
                        List<FolderSummary> folders, CursorPage<PostSummary> posts) implements FsResponse {

        public static FolderResult of(FolderListing listing) {
            return new FolderResult("folder", listing.folder(), listing.parent(), listing.folders(), listing.posts());
        }

    }

    record PostResult(String type, PostDetail post) implements FsResponse {

        public static PostResult of(PostDetail post) {
            return new PostResult("post", post);
        }

    }

}
