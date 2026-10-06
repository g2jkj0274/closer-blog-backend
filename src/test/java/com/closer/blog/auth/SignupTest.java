package com.closer.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.closer.blog.TestcontainersConfiguration;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.domain.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanUp() {
        folderRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void signupCreatesUserAndHomeFolder() throws Exception {
        signup("kim", "kim@example.com", "password1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/kim"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("kim"))
                .andExpect(jsonPath("$.displayName").value("kim"))
                .andExpect(jsonPath("$.joinedAt").isNotEmpty());

        User user = userRepository.findAll().getFirst();
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("password1", user.getPasswordHash())).isTrue();

        Folder home = folderRepository.findByOwnerIdAndParentIsNull(user.getId()).orElseThrow();
        assertThat(home.getName()).isEmpty();
        assertThat(home.getPath()).isEmpty();
    }

    @Test
    void usernameIsStoredInLowerCase() throws Exception {
        signup("Kim", "kim@example.com", "password1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("kim"));
    }

    @Test
    void mailOptInCanBeOmitted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                                { "username": "kim", "email": "kim@example.com", "password": "password1", "termsAgreed": true }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        signup("kim", "kim@example.com", "password1").andExpect(status().isCreated());

        signup("KIM", "other@example.com", "password1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        assertThat(folderRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateEmailIgnoringCaseIsRejected() throws Exception {
        signup("kim", "kim@example.com", "password1").andExpect(status().isCreated());

        signup("lee", "KIM@Example.com", "password1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""                                           
                                { "username": "k!", "email": "kim@example.com", "password": "short", "termsAgreed": false }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("username", "password", "termsAgreed")));
    }

    @Test
    void passwordIsLimitedTo72Utf8Bytes() throws Exception {
        // 한글 25자 = 75바이트
        signup("kim", "kim@example.com", "가".repeat(25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        // 한글 24자 = 72바이트
        signup("kim", "kim@example.com", "가".repeat(24))
                .andExpect(status().isCreated());
    }

    @Test
    void availability() throws Exception {
        signup("kim", "kim@example.com", "password1").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/availability").param("username", "KIM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(get("/api/v1/auth/availability").param("username", "lee"))
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/v1/auth/availability").param("email", "KIM@example.com"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void availabilityNeedsExactlyOneValidParameter() throws Exception {
        mockMvc.perform(get("/api/v1/auth/availability"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/availability").param("username", "kim").param("email", "kim@example.com"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/availability").param("username", "k!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void databaseReportsConstraintNames() {
        // 서비스의 사전 검사를 건너뛰고 DB 고유 제약에 직접 걸리게 한다.
        // GlobalExceptionHandler가 이 이름으로 409를 고른다.
        userRepository.saveAndFlush(User.register("kim", "kim@example.com", "x", false, Instant.now()));

        assertThatThrownBy(() -> userRepository
                .saveAndFlush(User.register("kim", "other@example.com", "x", false, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class)
                .cause().extracting("constraintName").isEqualTo("uq_users_username");

        assertThatThrownBy(() -> userRepository
                .saveAndFlush(User.register("lee", "KIM@example.com", "x", false, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .cause().extracting("constraintName").isEqualTo("uq_users_email");
    }

    private ResultActions signup(String username, String email, String password) throws Exception {
        String body = """                                                                                                   
                                { "username": "%s", "email": "%s", "password": "%s", "termsAgreed": true, "mailOptIn": false }
                                """.formatted(username, email, password);
        return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
