package com.closer.blog.auth.dto;

import com.closer.blog.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        // 아이디 또는 이메일. @가 있으면 이메일로 본다
        @NotBlank(message = "아이디 또는 이메일을 입력하세요.")
        String login,

        @NotBlank(message = "비밀번호를 입력하세요.")
        @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다. UTF-8로 72바이트 이하여야 합니다.")
        String password,

        // 빠지면 false
        Boolean keepLoggedIn) {
}