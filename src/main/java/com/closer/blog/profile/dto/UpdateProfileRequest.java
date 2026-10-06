package com.closer.blog.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * config 화면의 저장 (docs/api-spec.md 6절 PUT /me/profile). 세 필드를 모두 보낸다. ""이면 비운다.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "표시 이름을 입력하세요.")
        @Size(max = 40, message = "표시 이름은 40자 이하입니다.")
        String displayName,

        @NotNull(message = "bio를 보내세요. 비우려면 \"\"를 보냅니다.")
        @Size(max = 100, message = "한 줄 소개는 100자 이하입니다.")
        String bio,

        @NotNull(message = "contact를 보내세요. 비우려면 \"\"를 보냅니다.")
        @Size(max = 100, message = "연락처는 100자 이하입니다.")
        String contact) {

    // 앞뒤 공백을 잘라 낸 뒤에 검증한다. JSON을 읽을 때 이 생성자를 거친다
    public UpdateProfileRequest {
        displayName = (displayName == null) ? null : displayName.strip();
    }

}
