package com.closer.blog.fs.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.fs.dto.FsResponse;
import com.closer.blog.fs.service.FsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.FOLDER)
@RestController
@RequestMapping("/api/v1/fs")
@RequiredArgsConstructor
public class FsController {

    private final FsService fsService;

    @Operation(summary = "경로를 폴더나 글로 바꾸기 (cd, ls, cat, vi)",
            description = "path는 ~ 또는 /users/{아이디}로 시작한다. .md로 끝나면 글이다.")
    @GetMapping
    FsResponse resolve(@CurrentUserId Long viewerId, @RequestParam String path) {
        return fsService.resolve(viewerId, path);
    }

}
