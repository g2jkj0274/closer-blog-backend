package com.closer.blog.auth.controller;

import java.net.URI;

import com.closer.blog.auth.dto.AvailabilityResponse;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.dto.SignupResponse;
import com.closer.blog.auth.service.AuthService;
import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final UserService userService;

    @PostMapping("/signup")
    ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.getUsername()))
                .body(SignupResponse.from(user));
    }

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

}
