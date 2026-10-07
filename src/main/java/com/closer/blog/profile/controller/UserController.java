package com.closer.blog.profile.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.profile.dto.UserCard;
import com.closer.blog.profile.dto.UserProfile;
import com.closer.blog.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    // ls /users, find @kim
    @GetMapping
    CursorPage<UserCard> list(@RequestParam(required = false)
                              @Size(min = 1, max = 20, message = "q는 1~20자입니다.") String q,
                              @RequestParam(defaultValue = "posts")
                              @Pattern(regexp = "posts|joined", message = "sort는 posts 또는 joined입니다.") String sort,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "20")
                              @Min(value = 1, message = "size는 1~50입니다.")
                              @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return profileService.list(q, sort.equals("joined"), cursor, size);
    }

    // cd /users/kim의 프로필 패널
    @GetMapping("/{username}")
    UserProfile profile(@CurrentUserId Long viewerId, @PathVariable String username) {
        return profileService.profile(viewerId, username);
    }

}
