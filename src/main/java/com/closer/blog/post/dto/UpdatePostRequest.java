package com.closer.blog.post.dto;

import java.util.List;

import com.closer.blog.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * :w, mv, chmod (docs/api-spec.md 8절 PATCH /posts/{id}). 보낸(null이 아닌) 필드만 바꾼다.
 * tags는 통째로 바꾸며 []이면 모두 뗀다.
 */
public record UpdatePostRequest(

        @Size(max = 200, message = "제목은 200자 이하입니다.")
        String title,

        @MaxUtf8Bytes(value = PostRules.MAX_CONTENT_BYTES, message = "본문은 1MB 이하여야 합니다.")
        String content,

        @Size(max = PostRules.MAX_TAGS, message = "태그는 10개까지 달 수 있습니다.")
        List<@NotBlank @Pattern(regexp = PostRules.TAG, message = PostRules.TAG_MESSAGE) String> tags,

        @Pattern(regexp = PostRules.FILE_NAME, message = PostRules.FILE_NAME_MESSAGE)
        String fileName,

        Long folderId,

        Integer mode,

        // 보내면 서버의 version과 같을 때만 저장한다. 편집기의 :w는 항상 보낸다
        Integer version) {
}