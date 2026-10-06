package com.closer.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.closer.blog.TestcontainersConfiguration;
import com.closer.blog.auth.domain.RefreshToken;
import com.closer.blog.auth.domain.RefreshTokenRepository;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.service.AuthService;
import com.closer.blog.auth.service.RefreshTokenService;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.user.domain.User;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class LoginTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

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

    @Test
    void loginWithUsernameIssuesTokens() throws Exception {
        MvcResult result = login("kim", "password1", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.username").value("kim"))
                .andExpect(jsonPath("$.user.displayName").value("kim"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                // 로그인 유지를 안 했으므로 세션 쿠키다
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age"))))
                .andReturn();

        // DB에는 원문이 아니라 해시만 있다
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        RefreshToken saved = refreshTokenRepository.findAll().getFirst();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(cookie.getValue()));
        assertThat(saved.getTokenHash()).isNotEqualTo(cookie.getValue());
        assertThat(saved.isPersistent()).isFalse();
        assertThat(saved.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void issuedAccessTokenPassesSecurity() throws Exception {
        String body = login("kim", "password1", false).andReturn().getResponse().getContentAsString();
        String accessToken = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        // 보안을 통과하면 토큰의 사용자 정보가 나온다
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kim"));
    }

    @Test
    void loginWithEmailIgnoringCase() throws Exception {
        login("KIM@Example.com", "password1", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("kim"));
    }

    @Test
    void keepLoggedInIssuesThirtyDayCookie() throws Exception {
        login("kim", "password1", true)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=2592000")));

        RefreshToken saved = refreshTokenRepository.findAll().getFirst();
        assertThat(saved.isPersistent()).isTrue();
        assertThat(saved.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(30)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void wrongPasswordAndUnknownUserLookTheSame() throws Exception {
        login("kim", "wrong-password", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        login("nobody", "password1", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(user().getFailedLoginCount()).isEqualTo(1);
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void fifthFailureLocksAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("kim", "wrong-password", false).andExpect(status().isUnauthorized());
        }

        // 잠긴 동안은 비밀번호가 맞아도 423
        login("kim", "password1", false)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        User user = user();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isCloseTo(Instant.now().plus(Duration.ofMinutes(15)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void loginWorksAgainAfterLockExpires() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("kim", "wrong-password", false);
        }
        // 잠금 시각을 과거로 돌린다
        jdbcTemplate.update("UPDATE users SET locked_until = now() - interval '1 minute' WHERE username = 'kim'");

        login("kim", "password1", false).andExpect(status().isOk());
        assertThat(user().getLockedUntil()).isNull();
    }

    @Test
    void successResetsFailureCount() throws Exception {
        login("kim", "wrong-password", false);
        login("kim", "wrong-password", false);
        assertThat(user().getFailedLoginCount()).isEqualTo(2);

        login("kim", "password1", false).andExpect(status().isOk());
        assertThat(user().getFailedLoginCount()).isZero();
    }

    @Test
    void blankLoginIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""                             
                                { "login": "", "password": "" }                                                                            
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions login(String login, String password, boolean keepLoggedIn) throws Exception {
        String body = """                                                                                                          
                                { "login": "%s", "password": "%s", "keepLoggedIn": %s }                                                    
                                """.formatted(login, password, keepLoggedIn);
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private User user() {
        return userRepository.findByUsername("kim").orElseThrow();
    }
}
