package com.closer.blog.common.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서(/v3/api-docs)와 Swagger UI(/swagger-ui/index.html). 운영 프로필에서는 끈다 (docs/tech-stack.md 3.7절).
 * 문서는 컨트롤러에서 만들어지고, 여기서는 제목, 태그 설명과 순서, JWT 인증을 정한다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("c1oser.dev API")
                        .version("v1")
                        .description("터미널 블로그 c1oser.dev의 REST API. 규칙과 오류 코드는 docs/api-spec.md에 있다."))
                // Swagger UI는 이 순서대로 보여 준다. 컨트롤러는 @Tag(name = ApiTags.…)로 여기에 들어온다
                .tags(List.of(
                        tag(ApiTags.AUTH, "회원가입, 로그인, 토큰 재발급, 로그아웃 (adduser, login, logout)"),
                        tag(ApiTags.PROFILE, "내 정보와 프로필 수정, 홈 요약, 사람 목록과 프로필 (whoami, config, ls /users)"),
                        tag(ApiTags.FOLDER, "폴더 만들기·지우기, 경로 조회, 폴더 안 목록, 트리 (mkdir, rmdir, cd, ls, cat)"),
                        tag(ApiTags.POST, "글 만들기·읽기·고치기·지우기, 사람별 글 목록 (touch, vi, mv, chmod, rm)"),
                        tag(ApiTags.TRASH, "휴지통 목록, 복구, 완전 삭제 (~/.trash, u, rm -f)"),
                        tag(ApiTags.SEARCH, "내용 검색, 이름 검색, 태그 자동완성, 태그별 글 (grep, find)")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("POST /api/v1/auth/login 응답의 accessToken")))
                // 모든 API가 기본으로 토큰을 요구한다
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    // 가입, 로그인, 토큰 재발급, 로그아웃은 토큰 없이 부른다. SecurityConfig의 permitAll과 맞춘다
    @Bean
    OpenApiCustomizer publicAuthEndpoints() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/v1/auth/")) {
                item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
            }
        });
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

}
