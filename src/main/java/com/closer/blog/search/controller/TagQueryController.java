package com.closer.blog.search.controller;

import com.closer.blog.common.config.ApiTags;
import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.search.dto.TagPosts;
import com.closer.blog.search.dto.TagSuggestions;
import com.closer.blog.search.service.SearchService;
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
 * 태그 조회. 태그를 만들고 다는 일은 글을 저장할 때 post·tag 패키지가 한다.
 */
@Tag(name = ApiTags.SEARCH)
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagQueryController {

    private final SearchService searchService;

    @Operation(summary = "태그 자동완성 (편집기 태그 입력)")
    @GetMapping
    TagSuggestions suggest(@CurrentUserId Long viewerId, @RequestParam String q,
                           @RequestParam(defaultValue = "10")
                           @Min(value = 1, message = "size는 1~20입니다.")
                           @Max(value = 20, message = "size는 1~20입니다.") int size) {
        return searchService.suggestTags(viewerId, q, size);
    }

    @Operation(summary = "태그별 글 목록 (태그 클릭)",
            description = "name은 # 없이 URL 인코딩해 보낸다. 없는 태그는 404가 아니라 빈 목록이다.")
    @GetMapping("/{name}/posts")
    TagPosts posts(@CurrentUserId Long viewerId, @PathVariable String name,
                   @RequestParam(defaultValue = "all")
                   @Pattern(regexp = "all|mine", message = "scope는 all 또는 mine입니다.") String scope,
                   @RequestParam(required = false) String cursor,
                   @RequestParam(defaultValue = "20")
                   @Min(value = 1, message = "size는 1~50입니다.")
                   @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return searchService.postsByTag(viewerId, name, scope.equals("mine"), cursor, size);
    }

}
