package com.closer.blog.auth.controller;

import java.net.URI;

import com.closer.blog.auth.dto.AvailabilityResponse;
import com.closer.blog.auth.dto.LoginRequest;
import com.closer.blog.auth.dto.LoginResponse;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.dto.SignupResponse;
import com.closer.blog.auth.service.AuthService;
import com.closer.blog.auth.service.AuthService.LoginResult;
import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.AUTH)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final UserService userService;

    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Operation(summary = "회원가입 (adduser)")
    @PostMapping("/signup")
    ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.getUsername()))
                .body(SignupResponse.from(user));
    }

    @Operation(summary = "아이디·이메일 중복 확인",
            description = "username과 email 중 하나만 보낸다.")
    @GetMapping("/availability")
    AvailabilityResponse availability(
            @RequestParam(required = false)
            @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "아이디는 영문·숫자 3~20자입니다.")
            String username,
            @RequestParam(required = false)
            @Email(message = "이메일 형식이 아닙니다.")
            @Size(max = 255, message = "이메일은 255자 이하입니다.")
            String email) {
        if ((username == null) == (email == null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "username과 email 중 하나만 보내세요.");
        }
        boolean available = (username != null) ? userService.isUsernameAvailable(username)
                : userService.isEmailAvailable(email);
        return new AvailabilityResponse(available);
    }

    @Operation(summary = "로그인 (login)",
            description = "응답 본문에 액세스 토큰을, refresh_token 쿠키에 리프레시 토큰을 준다.")
    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(result.refreshToken()).toString())
                .body(LoginResponse.of(result.accessToken(), result.user()));
    }

    @Operation(summary = "액세스 토큰 재발급",
            description = "앱을 켤 때와 401을 받았을 때 부른다. refresh_token 쿠키를 쓰고, 새 쿠키로 바꿔 준다.")
    @PostMapping("/refresh")
    ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            if (refreshToken == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED);
            }
            LoginResult result = authService.refresh(refreshToken);
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            // 유예 시간 안의 재요청이면 새 토큰이 없다. 쿠키는 건드리지 않는다
            if (result.refreshToken() != null) {
                builder.header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(result.refreshToken()).toString());
            }
            return builder.body(LoginResponse.of(result.accessToken(), result.user()));
        } catch (ApiException ex) {
            // 쓸 수 없는 쿠키는 브라우저에서도 지운다. 응답 본문은 GlobalExceptionHandler가 만든다
            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString());
            throw ex;
        }
    }

    @Operation(summary = "로그아웃 (logout)")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .build();

    }
}
