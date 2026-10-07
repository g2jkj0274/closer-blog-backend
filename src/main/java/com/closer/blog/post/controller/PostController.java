package com.closer.blog.post.controller;

import java.net.URI;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.post.dto.CreatePostRequest;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.UpdatePostRequest;
import com.closer.blog.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.POST)
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "글 만들기 (touch 뒤 첫 :w)")
    @PostMapping
    ResponseEntity<PostDetail> create(@CurrentUserId Long userId, @Valid @RequestBody CreatePostRequest request) {
        PostDetail post = postService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + post.id())).body(post);
    }

    @Operation(summary = "글 읽기 (cat)")
    @GetMapping("/{id}")
    PostDetail get(@CurrentUserId Long userId, @PathVariable Long id) {
        return postService.get(userId, id);
    }

    @Operation(summary = "글 고치기, 이름 변경, 이동, 공개 범위 (:w, mv, chmod)",
            description = "보낸 필드만 바꾼다. 편집기의 :w는 version을 함께 보내고, 서버와 다르면 409 POST_VERSION_CONFLICT다.")
    @PatchMapping("/{id}")
    PostDetail update(@CurrentUserId Long userId, @PathVariable Long id,
                      @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(userId, id, request);
    }

    @Operation(summary = "휴지통으로 보내기 (rm)")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        postService.moveToTrash(userId, id);
        return ResponseEntity.noContent().build();
    }

}
