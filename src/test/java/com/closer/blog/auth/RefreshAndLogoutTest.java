package com.closer.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.closer.blog.TestcontainersConfiguration;
import com.closer.blog.auth.domain.RefreshToken;
import com.closer.blog.auth.domain.RefreshTokenRepository;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.service.AuthService;
import com.closer.blog.auth.service.RefreshTokenService;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.user.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshAndLogoutTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        folderRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
    }

    // 새 쿠키가 나오고, 옛 토큰에 회전 표시가 되고, 같은 가족이 이어짐
    @Test
    void refreshRotatesToken() throws Exception {
        String first = loginAndGetCookie(false);

        String second = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("kim"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn().getResponse().getCookie("refresh_token").getValue();

        assertThat(second).isNotEqualTo(first);
        RefreshToken old = findByValue(first);
        RefreshToken current = findByValue(second);
        assertThat(old.getRotatedAt()).isNotNull();
        assertThat(current.getRotatedAt()).isNull();
        assertThat(current.getFamilyId()).isEqualTo(old.getFamilyId());

        // 새 토큰으로 다시 갱신할 수 있다
        refresh(second).andExpect(status().isOk());
    }

    // 회전해도 30일 쿠키 / 세션 쿠키가 유지됨
    @Test
    void rotatedTokenKeepsKeepLoggedInChoice() throws Exception {
        refresh(loginAndGetCookie(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=2592000")));
        refresh(loginAndGetCookie(false))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age"))));
    }

    // 401과 함께 쿠키 삭제 헤더
    @Test
    void missingOrUnknownCookieIsUnauthorizedAndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        refresh("not-a-real-token")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    // 만료된 토큰은 401
    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        String cookie = loginAndGetCookie(false);
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'");

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    // 30초 유예: 액세스 토큰만, 쿠키 없음
    @Test
    void oldTokenWithinGraceGetsAccessTokenOnly() throws Exception {
        String first = loginAndGetCookie(false);
        refresh(first).andExpect(status().isOk());

        // 다른 탭이 같은 옛 토큰으로 곧바로 갱신한 경우
        refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    // 탈취 의심: 가족 전체 폐기, 현재 토큰도 못 씀
    @Test
    void oldTokenAfterGraceRevokesWholeFamily() throws Exception {
        String first = loginAndGetCookie(false);
        String second = refresh(first).andReturn().getResponse().getCookie("refresh_token").getValue();

        // 회전된 지 1분이 지난 것으로 만든다
        jdbcTemplate.update("UPDATE refresh_tokens SET rotated_at = now() - interval '1 minute' WHERE rotated_at IS NOT NULL");

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // 가족 전체가 지워져서 현재 토큰도 쓸 수 없다
        assertThat(refreshTokenRepository.count()).isZero();
        refresh(second).andExpect(status().isUnauthorized());
    }

    // 두 요청이 동시에 와도 새 토큰은 하나
    @Test
    void concurrentRefreshRotatesOnlyOnce() throws Exception {
        String first = loginAndGetCookie(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> refresh(first).andReturn().getResponse().getStatus();
            List<Future<Integer>> results = executor.invokeAll(List.of(call, call));
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        }
        finally {
            executor.shutdown();
        }

        // 옛 토큰 1개 + 새 토큰 1개. 새 토큰이 둘 생기지 않는다
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    // 로그아웃은 그 부라우저만. 다른 부라우저는 그대로
    @Test
    void logoutRevokesOnlyThatLogin() throws Exception {
        String thisBrowser = loginAndGetCookie(false);
        String otherBrowser = loginAndGetCookie(false);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", thisBrowser)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        refresh(thisBrowser).andExpect(status().isUnauthorized());
        refresh(otherBrowser).andExpect(status().isOk());
    }

    // 쿠키가 없어도 204
    @Test
    void logoutWithoutCookieStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // 예약 작업이 만료된 것만 지움
    @Test
    void cleanupDeletesOnlyExpiredTokens() throws Exception {
        loginAndGetCookie(false);
        loginAndGetCookie(false);
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' "
                + "WHERE id = (SELECT min(id) FROM refresh_tokens)");

        assertThat(refreshTokenService.deleteExpired()).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    private String loginAndGetCookie(boolean keepLoggedIn) throws Exception {
        String body = """
                { "login": "kim", "password": "password1", "keepLoggedIn": %s }
                """.formatted(keepLoggedIn);
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token").getValue();
    }

    private ResultActions refresh(String cookieValue) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", cookieValue)));
    }

    private RefreshToken findByValue(String cookieValue) {
        return refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(cookieValue)).orElseThrow();
    }
}
