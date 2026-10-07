package com.closer.blog.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.closer.blog.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prometheus가 긁어 가는 지표 (docs/tech-stack.md 3.11절).
 */
class MonitoringTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Import(TestcontainersConfiguration.class)
    class Local {

        @Autowired
        MockMvc mockMvc;

        @Test
        void prometheusEndpointIsOpenAndHasWhatTheDashboardNeeds() throws Exception {
            // 요청 하나를 보내야 http_server_requests 지표가 생긴다
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

            String metrics = mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(metrics)
                    // Grafana가 이 라벨로 이 앱의 지표를 고른다
                    .contains("application=\"blog-server\"")
                    // p95를 계산하는 구간별 개수
                    .contains("http_server_requests_seconds_bucket")
                    .contains("jvm_memory_used_bytes")
                    .contains("hikaricp_connections_active");
        }

    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("prod")
    // 운영 프로필은 이 값들이 없으면 뜨지 않는다 (application-prod.yaml). DB는 Testcontainers가 넣는다
    @TestPropertySource(properties = {
            "JWT_SECRET=test-only-secret-for-prod-profile-0123456789",
            "CORS_ALLOWED_ORIGINS=https://c1oser.dev"
    })
    class Prod {

        private final HttpClient http = HttpClient.newHttpClient();

        @LocalServerPort
        int serverPort;

        @LocalManagementPort
        int managementPort;

        @Test
        void actuatorIsOnlyOnTheManagementPort() throws Exception {
            // application-prod.yaml이 Actuator를 별도 포트(8081)로 뺀다.
            // RANDOM_PORT 테스트에서는 그 포트도 빈 포트로 바뀌므로 API 포트와 다른지만 본다
            assertThat(managementPort).isNotEqualTo(serverPort);

            assertThat(statusOf(managementPort, "/actuator/prometheus")).isEqualTo(200);
            assertThat(statusOf(managementPort, "/actuator/health")).isEqualTo(200);
            // 공개하는 API 포트에는 지표가 없다
            assertThat(statusOf(serverPort, "/actuator/prometheus")).isEqualTo(404);
        }

        private int statusOf(int port, String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }

    }

}
