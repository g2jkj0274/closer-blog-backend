package com.closer.blog.common.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서(/v3/api-docs)와 Swagger UI(/swagger-ui/index.html). 운영 프로필에서는 끈다 (docs/tech-stack.md 3.7절).
 * 문서는 컨트롤러에서 만들어지고, 여기서는 제목과 JWT 인증만 정한다.
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

}
