package com.closer.blog.profile.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.profile.dto.MeResponse;
import com.closer.blog.profile.dto.MeSummary;
import com.closer.blog.profile.dto.UpdateProfileRequest;
import com.closer.blog.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = ApiTags.PROFILE)
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final ProfileService profileService;

    @Operation(summary = "내 정보 (whoami)")
    @GetMapping
    MeResponse me(@CurrentUserId Long userId) {
        return profileService.me(userId);
    }

    @Operation(summary = "프로필 수정 (config)",
            description = "세 필드를 모두 보낸다. 빈 문자열이면 비운다.")
    @PutMapping("/profile")
    MeResponse updateProfile(@CurrentUserId Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(userId, request);
    }

    @Operation(summary = "홈 요약 (neofetch)")
    @GetMapping("/summary")
    MeSummary summary(@CurrentUserId Long userId) {
        return profileService.summary(userId);
    }

}
