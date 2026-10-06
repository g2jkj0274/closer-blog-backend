package com.closer.blog.post.dto;

import java.util.List;

import com.closer.blog.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 첫 :w (docs/api-spec.md 8절 POST /posts). 이름 규칙은 docs/erd.md 3절.
 */
public record CreatePostRequest(

        @NotNull(message = "어느 폴더에 둘지 folderId를 보내세요.")
        Long folderId,

        @NotBlank(message = "파일 이름을 입력하세요.")
        @Pattern(regexp = PostRules.FILE_NAME, message = PostRules.FILE_NAME_MESSAGE)
        String fileName,

        @Size(max = 200, message = "제목은 200자 이하입니다.")
        String title,

        @MaxUtf8Bytes(value = PostRules.MAX_CONTENT_BYTES, message = "본문은 1MB 이하여야 합니다.")
        String content,

        @Size(max = PostRules.MAX_TAGS, message = "태그는 10개까지 달 수 있습니다.")
        List<@NotBlank @Pattern(regexp = PostRules.TAG, message = PostRules.TAG_MESSAGE) String> tags,

        // 644(기본) 또는 600. 서비스에서 검사한다
        Integer mode) {
}