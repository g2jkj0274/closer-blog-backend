package com.closer.blog.profile.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.profile.dto.UserCard;
import com.closer.blog.profile.dto.UserProfile;
import com.closer.blog.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사람 목록과 프로필. 그 사람의 글 목록(/users/{username}/posts)은 post, 트리는 fs 패키지가 맡는다.
 */
@Tag(name = ApiTags.PROFILE)
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profileService;

    @Operation(summary = "사람 목록·찾기 (ls /users, find @아이디)",
            description = "q는 앞뒤 공백을 자른 뒤 1~20자다. 글 수는 공개 글 수다.")
    @GetMapping
    CursorPage<UserCard> list(@RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "posts")
                              @Pattern(regexp = "posts|joined", message = "sort는 posts 또는 joined입니다.") String sort,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "20")
                              @Min(value = 1, message = "size는 1~50입니다.")
                              @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return profileService.list(q, sort.equals("joined"), cursor, size);
    }

    @Operation(summary = "프로필 (cd /users/{아이디})")
    @GetMapping("/{username}")
    UserProfile profile(@CurrentUserId Long viewerId, @PathVariable String username) {
        return profileService.profile(viewerId, username);
    }

}
