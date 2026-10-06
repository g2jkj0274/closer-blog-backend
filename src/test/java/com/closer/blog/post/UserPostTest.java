package com.closer.blog.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class UserPostTest {

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
    void ownerSeesAllPostsNewestFirst() throws Exception {
        create("{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(kimHomeId));
        create("{ \"folderId\": %d, \"fileName\": \"b.md\", \"mode\": 600 }".formatted(kimHomeId));
        create("{ \"folderId\": %d, \"fileName\": \"c.md\", \"title\": \"포인터\", \"tags\": [\"포인터\", \"예제\"] }"
                .formatted(kimPythonId));

        list(kimToken, "kim", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].fileName").value("c.md"))
                .andExpect(jsonPath("$.items[0].path").value("python/c.md"))
                .andExpect(jsonPath("$.items[0].displayPath").value("~/python/c.md"))
                .andExpect(jsonPath("$.items[0].title").value("포인터"))
                .andExpect(jsonPath("$.items[0].tags[0]").value("포인터"))
                .andExpect(jsonPath("$.items[0].tags[1]").value("예제"))
                .andExpect(jsonPath("$.items[0].owner.username").value("kim"))
                .andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty())
                // 목록에는 본문을 싣지 않는다
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[1].fileName").value("b.md"))
                .andExpect(jsonPath("$.items[2].fileName").value("a.md"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void othersSeeOnlyPublicPosts() throws Exception {
        create("{ \"folderId\": %d, \"fileName\": \"public.md\" }".formatted(kimPythonId));
        create("{ \"folderId\": %d, \"fileName\": \"secret.md\", \"mode\": 600 }".formatted(kimHomeId));

        list(leeToken, "kim", "")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].fileName").value("public.md"))
                .andExpect(jsonPath("$.items[0].displayPath").value("/users/kim/python/public.md"));
    }

    @Test
    void trashedPostsAreHidden() throws Exception {
        Long id = create("{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(kimHomeId));
        mockMvc.perform(delete("/api/v1/posts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken))
                .andExpect(status().isNoContent());

        list(kimToken, "kim", "").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void editedPostMovesToTop() throws Exception {
        Long a = create("{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(kimHomeId));
        create("{ \"folderId\": %d, \"fileName\": \"b.md\" }".formatted(kimHomeId));

        mockMvc.perform(patch("/api/v1/posts/" + a)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"고침\" }"))
                .andExpect(status().isOk());

        list(kimToken, "kim", "").andExpect(jsonPath("$.items[0].id").value(a));
    }

    @Test
    void pagedByCursor() throws Exception {
        Long a = create("{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(kimHomeId));
        Long b = create("{ \"folderId\": %d, \"fileName\": \"b.md\" }".formatted(kimHomeId));
        Long c = create("{ \"folderId\": %d, \"fileName\": \"c.md\" }".formatted(kimHomeId));

        String body = list(kimToken, "kim", "?size=2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(c))
                .andExpect(jsonPath("$.items[1].id").value(b))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = body.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        list(kimToken, "kim", "?size=2&cursor=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(a))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void usernameIsCaseInsensitiveAndUnknownIsNotFound() throws Exception {
        create("{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(kimHomeId));

        list(leeToken, "KIM", "").andExpect(jsonPath("$.items.length()").value(1));
        list(leeToken, "nobody", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void badPagingParamsAreRejected() throws Exception {
        list(kimToken, "kim", "?size=51").andExpect(status().isBadRequest());
        list(kimToken, "kim", "?cursor=not-a-cursor").andExpect(status().isBadRequest());
    }

    private Long create(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(response.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions list(String token, String username, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/users/" + username + "/posts" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
