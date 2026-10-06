package com.closer.blog.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateFolderRequest(

        @NotNull(message = "어느 폴더 아래에 만들지 parentId를 보내세요.")
        Long parentId,

        // 1~50자. /, 공백, 제어 문자 불가. .으로 시작하거나 .md로 끝날 수 없다 (docs/erd.md 3절 "이름 규칙")
        @NotBlank(message = "폴더 이름을 입력하세요.")
        @Pattern(regexp = "^(?!\\.)(?!.*\\.[mM][dD]$)[^/\\s\\p{Cntrl}]{1,50}$",
                message = "폴더 이름은 1~50자이고 /, 공백을 쓸 수 없습니다. .으로 시작하거나 .md로 끝날 수 없습니다.")
        String name) {
}