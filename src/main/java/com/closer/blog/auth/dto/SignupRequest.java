package com.closer.blog.auth.dto;

import com.closer.blog.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "아이디를 입력하세요.")
        @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "아이디는 영문·숫자 3~20자입니다.")
        String username,

        @NotBlank(message = "이메일을 입력하세요.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하입니다.")
        String email,

        @NotNull(message = "비밀번호를 입력하세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상입니다.")
        // BCrypt는 72바이트까지만 쓴다. 한글은 한 글자가 3바이트다 (docs/api-spec.md 5절)
        @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다. UTF-8로 72바이트 이하여야 합니다.")
        String password,

        // boolean 대신 Boolean: 필드가 빠진 요청을 JSON 오류(필드 정보 없음)가 아닌 검증 오류로 받는다
        @NotNull(message = "필수 약관에 동의해야 합니다.")
        @AssertTrue(message = "필수 약관에 동의해야 합니다.")
        Boolean termsAgreed,

        // 선택 동의. 빠지면 false
        Boolean mailOptIn) {
}