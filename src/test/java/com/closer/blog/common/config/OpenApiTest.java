package com.closer.blog.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.closer.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class OpenApiTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    class Local {

        @Autowired
        MockMvc mockMvc;

        @Test
        void apiDocsArePublicAndDescribeJwt() throws Exception {
            // 로그인하지 않아도 볼 수 있다
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("c1oser.dev API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/posts'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/search/content'].get").exists())
                    // 토큰이 필요 없는 API는 security를 비운다
                    .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                    // 로그인한 사용자 id(@CurrentUserId)는 요청 파라미터가 아니다
                    .andExpect(jsonPath("$.paths['/api/v1/me'].get.parameters").doesNotExist());
        }

        @Test
        void swaggerUiIsPublic() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }

    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("prod")
    class Prod {

        @Autowired
        MockMvc mockMvc;

        @Test
        void apiDocsAndSwaggerUiAreOff() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }

    }

}
