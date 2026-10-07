package com.closer.blog.post.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.RestorePostRequest;
import com.closer.blog.post.dto.TrashItem;
import com.closer.blog.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.TRASH)
@RestController
@RequestMapping("/api/v1/trash")
@RequiredArgsConstructor
public class TrashController {

    private final PostService postService;

    @Operation(summary = "휴지통 목록 (cd ~/.trash)")
    @GetMapping
    CursorPage<TrashItem> list(@CurrentUserId Long userId,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "20")
                               @Min(value = 1, message = "size는 1~50입니다.")
                               @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return postService.listTrash(userId, cursor, size);
    }

    @Operation(summary = "휴지통에서 복구 (u)",
            description = "본문 없이 보내면 원래 이름으로 복구한다. 그 자리에 같은 이름의 글이 있으면 409이고, fileName을 보내 새 이름으로 복구한다.")
    @PostMapping("/{id}/restore")
    PostDetail restore(@CurrentUserId Long userId, @PathVariable Long id,
                       @Valid @RequestBody(required = false) RestorePostRequest request) {
        return postService.restore(userId, id, (request == null) ? null : request.fileName());
    }

    @Operation(summary = "완전 삭제 (휴지통에서 rm -f)",
            description = "되돌릴 수 없다. 휴지통에 있는 글만 지운다.")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> purge(@CurrentUserId Long userId, @PathVariable Long id) {
        postService.purge(userId, id);
        return ResponseEntity.noContent().build();
    }

}
