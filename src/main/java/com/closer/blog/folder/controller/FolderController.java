package com.closer.blog.folder.controller;

import java.net.URI;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.dto.CreateFolderRequest;
import com.closer.blog.folder.dto.FolderSummary;
import com.closer.blog.folder.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.FOLDER)
@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @Operation(summary = "폴더 만들기 (mkdir)")
    @PostMapping
    ResponseEntity<FolderSummary> create(@CurrentUserId Long userId,
                                         @Valid @RequestBody CreateFolderRequest request) {
        Folder folder = folderService.create(userId, request.parentId(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/folders/" + folder.getId()))
                .body(FolderSummary.created(folder));
    }

    @Operation(summary = "빈 폴더 지우기 (rmdir)",
            description = "하위 폴더나 글(휴지통 글 포함)이 있으면 409 FOLDER_NOT_EMPTY다.")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        folderService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

}