package com.closer.blog.search.controller;

import com.closer.blog.common.security.CurrentUserId;
import com.closer.blog.search.dto.ContentSearchResult;
import com.closer.blog.search.dto.NameSearchResult;
import com.closer.blog.search.service.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    // grep. q의 길이는 앞뒤 공백을 자른 뒤 서비스가 본다
    @GetMapping("/content")
    ContentSearchResult content(@CurrentUserId Long viewerId, @RequestParam String q,
                                @RequestParam(defaultValue = "all")
                                @Pattern(regexp = "all|mine", message = "scope는 all 또는 mine입니다.") String scope,
                                @RequestParam(required = false) String cursor,
                                @RequestParam(defaultValue = "20")
                                @Min(value = 1, message = "size는 1~50입니다.")
                                @Max(value = 50, message = "size는 1~50입니다.") int size) {
        return searchService.searchContent(viewerId, q, scope.equals("mine"), cursor, size);
    }

    // find. find @kim은 GET /users?q=kim이다
    @GetMapping("/names")
    NameSearchResult names(@CurrentUserId Long viewerId, @RequestParam String q,
                           @RequestParam(defaultValue = "all")
                           @Pattern(regexp = "all|mine", message = "scope는 all 또는 mine입니다.") String scope) {
        return searchService.searchNames(viewerId, q, scope.equals("mine"));
    }

}
