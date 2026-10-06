package com.closer.blog.post.controller;

import java.net.URI;

import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.post.dto.CreatePostRequest;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.UpdatePostRequest;
import com.closer.blog.post.service.PostService;
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

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    ResponseEntity<PostDetail> create(@CurrentUserId Long userId, @Valid @RequestBody CreatePostRequest request) {
        PostDetail post = postService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + post.id())).body(post);
    }

    @GetMapping("/{id}")
    PostDetail get(@CurrentUserId Long userId, @PathVariable Long id) {
        return postService.get(userId, id);
    }

    @PatchMapping("/{id}")
    PostDetail update(@CurrentUserId Long userId, @PathVariable Long id,
                      @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(userId, id, request);
    }

    // rm. 휴지통으로 보낸다
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        postService.moveToTrash(userId, id);
        return ResponseEntity.noContent().build();
    }

}
