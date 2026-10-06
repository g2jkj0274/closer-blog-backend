package com.closer.blog.profile.controller;

import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.profile.dto.MeResponse;
import com.closer.blog.profile.dto.MeSummary;
import com.closer.blog.profile.dto.UpdateProfileRequest;
import com.closer.blog.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final ProfileService profileService;

    // whoami
    @GetMapping
    MeResponse me(@CurrentUserId Long userId) {
        return profileService.me(userId);
    }

    // config 화면의 저장
    @PutMapping("/profile")
    MeResponse updateProfile(@CurrentUserId Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(userId, request);
    }

    // 홈 화면의 neofetch
    @GetMapping("/summary")
    MeSummary summary(@CurrentUserId Long userId) {
        return profileService.summary(userId);
    }

}
