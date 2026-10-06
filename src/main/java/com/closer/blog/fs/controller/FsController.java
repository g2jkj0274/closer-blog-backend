package com.closer.blog.fs.controller;

import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.fs.dto.FsResponse;
import com.closer.blog.fs.service.FsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fs")
@RequiredArgsConstructor
public class FsController {

    private final FsService fsService;

    // cd, ls, cat, vi가 모두 여기서 시작한다
    @GetMapping
    FsResponse resolve(@CurrentUserId Long viewerId, @RequestParam String path) {
        return fsService.resolve(viewerId, path);
    }

}
