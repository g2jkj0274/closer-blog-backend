package com.closer.blog.post.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 휴지통에서 복구 (docs/api-spec.md 8절 POST /trash/{id}/restore).
 * 원래 자리에 같은 이름의 글이 생겨 있을 때만 새 이름을 보낸다.
 */
public record RestorePostRequest(

        @Pattern(regexp = PostRules.FILE_NAME, message = PostRules.FILE_NAME_MESSAGE)
        String fileName) {
}
