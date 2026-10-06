package com.closer.blog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.closer.blog.DatabaseCleaner;
import com.closer.blog.TestcontainersConfiguration;
import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.auth.service.AuthService;
import com.closer.blog.common.security.JwtProvider;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.folder.service.FolderService;
import com.closer.blog.post.domain.PostRepository;
import com.closer.blog.post.service.PostService;
import com.closer.blog.user.domain.User;
import org.junit.jupiter.api.AfterEach;
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
class TrashTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    FolderRepository folderRepository;

    @Autowired
    FolderService folderService;

    @Autowired
    PostRepository postRepository;

    @Autowired
    PostService postService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    String kimToken;

    String leeToken;

    Long kimHomeId;

    Long kimPythonId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        User kim = authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
        User lee = authService.signup(new SignupRequest("lee", "lee@example.com", "password1", true, false));
        kimToken = jwtProvider.issue(kim.getId(), kim.getUsername()).value();
        leeToken = jwtProvider.issue(lee.getId(), lee.getUsername()).value();
        kimHomeId = folderRepository.findByOwnerIdAndParentIsNull(kim.getId()).orElseThrow().getId();
        kimPythonId = folderService.create(kim.getId(), kimHomeId, "python").getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void rmMovesPostToTrash() throws Exception {
        Long id = createIn(kimPythonId, "pointer.md");

        rm(kimToken, id).andExpect(status().isNoContent());

        read(kimToken, id).andExpect(status().isNotFound());
        trash(kimToken, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].fileName").value("pointer.md"))
                .andExpect(jsonPath("$.items[0].originalDisplayPath").value("~/python/pointer.md"))
                .andExpect(jsonPath("$.items[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].purgeAt").isNotEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void rmChecksPermission() throws Exception {
        Long id = createIn(kimHomeId, "a.md");

        rm(leeToken, id).andExpect(status().isForbidden());
        rm(kimToken, 999_999L).andExpect(status().isNotFound());
        rm(kimToken, id).andExpect(status().isNoContent());
        // 이미 휴지통에 있으면 없는 글과 같다
        rm(kimToken, id).andExpect(status().isNotFound());
    }

    @Test
    void trashedNameCanBeReused() throws Exception {
        Long id = createIn(kimHomeId, "a.md");
        rm(kimToken, id);

        createIn(kimHomeId, "a.md");
    }

    @Test
    void restoreToOriginalPlace() throws Exception {
        Long id = createIn(kimPythonId, "pointer.md");
        rm(kimToken, id);

        // 본문 없이 보낸다
        mockMvc.perform(post("/api/v1/trash/" + id + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("python/pointer.md"));
        read(kimToken, id).andExpect(status().isOk());
        trash(kimToken, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void restoreWithNewNameWhenTaken() throws Exception {
        Long id = createIn(kimHomeId, "a.md");
        rm(kimToken, id);
        createIn(kimHomeId, "a.md");

        restore(kimToken, id, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NAME_TAKEN"));
        restore(kimToken, id, "{ \"fileName\": \"a b.md\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fileName"));
        restore(kimToken, id, "{ \"fileName\": \"a-2.md\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("a-2.md"));
    }

    @Test
    void onlyMyTrashCanBeRestoredOrPurged() throws Exception {
        Long live = createIn(kimHomeId, "live.md");
        Long trashed = createIn(kimHomeId, "trashed.md");
        rm(kimToken, trashed);

        // 살아 있는 글은 휴지통에 없다
        restore(kimToken, live, "{}").andExpect(status().isNotFound());
        purge(kimToken, live).andExpect(status().isNotFound());
        // 남의 휴지통은 보이지 않는다
        restore(leeToken, trashed, "{}").andExpect(status().isNotFound());
        purge(leeToken, trashed).andExpect(status().isNotFound());
        trash(leeToken, "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void purgeDeletesForGood() throws Exception {
        Long id = create("{ \"folderId\": %d, \"fileName\": \"a.md\", \"tags\": [\"x\"] }".formatted(kimPythonId));
        rm(kimToken, id);

        purge(kimToken, id).andExpect(status().isNoContent());

        assertThat(postRepository.existsById(id)).isFalse();
        // 글이 없어졌으니 폴더를 지울 수 있다
        mockMvc.perform(delete("/api/v1/folders/" + kimPythonId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void trashIsPagedNewestFirst() throws Exception {
        Long a = createIn(kimHomeId, "a.md");
        Long b = createIn(kimHomeId, "b.md");
        Long c = createIn(kimHomeId, "c.md");
        rm(kimToken, a);
        rm(kimToken, b);
        rm(kimToken, c);

        String body = trash(kimToken, "?size=2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(c))
                .andExpect(jsonPath("$.items[1].id").value(b))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = body.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        trash(kimToken, "?size=2&cursor=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(a))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void badPagingParamsAreRejected() throws Exception {
        trash(kimToken, "?size=51").andExpect(status().isBadRequest());
        trash(kimToken, "?size=0").andExpect(status().isBadRequest());
        trash(kimToken, "?cursor=not-a-cursor")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void purgeExpiredDeletesOnlyOldTrash() throws Exception {
        Long old = createIn(kimHomeId, "old.md");
        Long recent = createIn(kimHomeId, "recent.md");
        Long live = createIn(kimHomeId, "live.md");
        rm(kimToken, old);
        rm(kimToken, recent);
        jdbcTemplate.update("UPDATE posts SET deleted_at = now() - interval '31 days' WHERE id = ?", old);

        assertThat(postService.purgeExpired()).isEqualTo(1);
        assertThat(postRepository.existsById(old)).isFalse();
        assertThat(postRepository.existsById(recent)).isTrue();
        assertThat(postRepository.existsById(live)).isTrue();
    }

    private Long create(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private Long createIn(Long folderId, String fileName) throws Exception {
        return create("{ \"folderId\": %d, \"fileName\": \"%s\" }".formatted(folderId, fileName));
    }

    private ResultActions read(String token, Long id) throws Exception {
        return mockMvc.perform(get("/api/v1/posts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions rm(String token, Long id) throws Exception {
        return mockMvc.perform(delete("/api/v1/posts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions trash(String token, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/trash" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions restore(String token, Long id, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/trash/" + id + "/restore")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions purge(String token, Long id) throws Exception {
        return mockMvc.perform(delete("/api/v1/trash/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
