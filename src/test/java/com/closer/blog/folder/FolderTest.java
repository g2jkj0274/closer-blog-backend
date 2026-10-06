package com.closer.blog.folder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.closer.blog.DatabaseCleaner;
import com.closer.blog.TestcontainersConfiguration;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.service.AuthService;
import com.closer.blog.common.security.JwtProvider;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class FolderTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String kimToken;

    Long kimHomeId;

    Long leeHomeId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        User kim = authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
        User lee = authService.signup(new SignupRequest("lee", "lee@example.com", "password1", true, false));
        kimToken = jwtProvider.issue(kim.getId(), kim.getUsername()).value();
        kimHomeId = homeIdOf(kim);
        leeHomeId = homeIdOf(lee);
    }

    // 다른 테스트 클래스에 글(posts) 행을 남기지 않는다
    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void mkdirUnderHome() throws Exception {
        mkdir(kimHomeId, "python")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/folders/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("python"))
                .andExpect(jsonPath("$.path").value("python"))
                .andExpect(jsonPath("$.displayPath").value("~/python"))
                .andExpect(jsonPath("$.postCount").value(0))
                .andExpect(jsonPath("$.folderCount").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void mkdirNestedAndKoreanName() throws Exception {
        Long python = idOf(mkdir(kimHomeId, "python"));

        mkdir(python, "자료구조")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("python/자료구조"))
                .andExpect(jsonPath("$.displayPath").value("~/python/자료구조"));
    }

    @Test
    void duplicateNameInSameFolderIsRejected() throws Exception {
        Long python = idOf(mkdir(kimHomeId, "python"));
        mkdir(kimHomeId, "python")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NAME_TAKEN"));

        // 다른 폴더 아래라면 같은 이름도 된다
        mkdir(python, "python").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = { "a b", "a/b", ".hidden", "notes.md", "NOTES.MD" })
    void invalidNamesAreRejected(String name) throws Exception {
        mkdir(kimHomeId, name)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void nameIsLimitedToFiftyCharacters() throws Exception {
        mkdir(kimHomeId, "가".repeat(50)).andExpect(status().isCreated());
        mkdir(kimHomeId, "나".repeat(51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void cannotMkdirInOthersFolder() throws Exception {
        mkdir(leeHomeId, "python")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownParentIsNotFound() throws Exception {
        mkdir(999_999L, "python").andExpect(status().isNotFound());
    }

    @Test
    void depthIsLimitedToTen() throws Exception {
        Long parent = kimHomeId;
        for (int depth = 1; depth <= 10; depth++) {
            parent = idOf(mkdir(parent, "d" + depth));
        }

        mkdir(parent, "d11")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"parentId\": %d, \"name\": \"python\" }".formatted(kimHomeId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rmdirEmptyFolder() throws Exception {
        Long python = idOf(mkdir(kimHomeId, "python"));

        rmdir(python).andExpect(status().isNoContent());
        assertThat(folderRepository.existsById(python)).isFalse();
    }

    @Test
    void cannotRmdirHome() throws Exception {
        rmdir(kimHomeId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void cannotRmdirFolderWithSubfolder() throws Exception {
        Long python = idOf(mkdir(kimHomeId, "python"));
        mkdir(python, "basic");

        rmdir(python)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));
    }

    @Test
    void cannotRmdirFolderWithPostInTrash() throws Exception {
        Long python = idOf(mkdir(kimHomeId, "python"));
        // 글 API는 아직 없으므로 휴지통에 있는 글을 DB에 직접 넣는다
        jdbcTemplate.update("INSERT INTO posts (owner_id, folder_id, file_name, deleted_at) "
                + "SELECT owner_id, id, 'old.md', now() FROM folders WHERE id = ?", python);

        rmdir(python)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));
        assertThat(folderRepository.existsById(python)).isTrue();
    }

    @Test
    void cannotRmdirOthersFolderAndUnknownIsNotFound() throws Exception {
        rmdir(leeHomeId).andExpect(status().isForbidden());
        rmdir(999_999L).andExpect(status().isNotFound());
    }

    private ResultActions mkdir(Long parentId, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/folders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"parentId\": %d, \"name\": \"%s\" }".formatted(parentId, name)));
    }

    private ResultActions rmdir(Long folderId) throws Exception {
        return mockMvc.perform(delete("/api/v1/folders/" + folderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken));
    }

    private Long idOf(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private Long homeIdOf(User user) {
        return folderRepository.findByOwnerIdAndParentIsNull(user.getId()).orElseThrow().getId();
    }
}
