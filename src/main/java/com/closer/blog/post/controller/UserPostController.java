package com.closer.blog.post.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.post.dto.PostSummary;
import com.closer.blog.post.service.PostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.POST)
@RestController
@RequestMapping("/api/v1/users/{username}/posts")
@RequiredArgsConstructor
public class UserPostController {

    private final PostService postService;

    // cd /users/kim의 가운데 목록. 폴더와 상관없이 최근 수정순
    @GetMapping
    CursorPage<PostSummary> list(@CurrentUserId Long viewerId, @PathVariable String username,
                                 @RequestParam(required = false) String cursor,
                                 @RequestParam(defaultValue = "20")
                                 @Min(value = 1, message = "size는 1~50입니다.")
                                 @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return postService.listByOwner(viewerId, username, cursor, size);
    }

}
