package com.closer.blog.folder.dto;

import java.time.Instant;

import com.closer.blog.folder.domain.Folder;

/**
 * 폴더 카드와 트리 노드 (docs/api-spec.md 4절).
 */
public record FolderSummary(Long id, String name, String path, String displayPath, long postCount,
                            long folderCount, Instant createdAt) {

    /**
     * 방금 만든 내 폴더. 안에 아무것도 없고, 보는 사람이 소유자이므로 경로가 ~로 시작한다.
     */
    public static FolderSummary created(Folder folder) {
        return new FolderSummary(folder.getId(), folder.getName(), folder.getPath(), "~/" + folder.getPath(), 0, 0,
                folder.getCreatedAt());
    }

}