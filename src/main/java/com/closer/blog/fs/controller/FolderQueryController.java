package com.closer.blog.fs.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.fs.dto.FolderListing;
import com.closer.blog.fs.dto.FolderTree;
import com.closer.blog.fs.service.FsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 정보가 함께 나가는 폴더 조회. 폴더 만들기·지우기는 folder 패키지의 FolderController가 맡는다.
 */
@Tag(name = ApiTags.FOLDER)
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FolderQueryController {

    private final FsService fsService;

    @Operation(summary = "폴더 안 목록 (ls, cd)")
    @GetMapping("/folders/{id}")
    FolderListing get(@CurrentUserId Long viewerId, @PathVariable Long id,
                      @RequestParam(defaultValue = "name")
                      @Pattern(regexp = "name|updated", message = "sort는 name 또는 updated입니다.") String sort,
                      @RequestParam(required = false) String cursor,
                      @RequestParam(defaultValue = "" + FsService.DEFAULT_POST_PAGE_SIZE)
                      @Min(value = 1, message = "size는 1~50입니다.")
                      @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return fsService.listFolder(viewerId, id, sort.equals("updated"), cursor, size);
    }

    @Operation(summary = "폴더 트리 (왼쪽 트리)")
    @GetMapping("/users/{username}/tree")
    FolderTree tree(@CurrentUserId Long viewerId, @PathVariable String username) {
        return fsService.tree(viewerId, username);
    }

}
