package com.closer.blog.fs;

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
class FsTest {

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

    Long pythonId;

    Long basicId;

    Long algoId;

    // kim의 폴더: ~, ~/python, ~/python/basic, ~/algo (python을 algo보다 먼저 만든다)
    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        User kim = authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
        User lee = authService.signup(new SignupRequest("lee", "lee@example.com", "password1", true, false));
        kimToken = jwtProvider.issue(kim.getId(), kim.getUsername()).value();
        leeToken = jwtProvider.issue(lee.getId(), lee.getUsername()).value();
        kimHomeId = folderRepository.findByOwnerIdAndParentIsNull(kim.getId()).orElseThrow().getId();
        pythonId = folderService.create(kim.getId(), kimHomeId, "python").getId();
        basicId = folderService.create(kim.getId(), pythonId, "basic").getId();
        algoId = folderService.create(kim.getId(), kimHomeId, "algo").getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void treeHasAllFoldersSortedByName() throws Exception {
        createIn(kimHomeId, "root.md", 644);
        createIn(pythonId, "a.md", 644);
        createIn(pythonId, "b.md", 600);

        tree(kimToken, "kim")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root.id").value(kimHomeId))
                .andExpect(jsonPath("$.root.path").value(""))
                .andExpect(jsonPath("$.root.displayPath").value("~"))
                .andExpect(jsonPath("$.root.postCount").value(1))
                .andExpect(jsonPath("$.root.folderCount").value(2))
                .andExpect(jsonPath("$.root.children[0].name").value("algo"))
                .andExpect(jsonPath("$.root.children[0].children.length()").value(0))
                .andExpect(jsonPath("$.root.children[1].name").value("python"))
                .andExpect(jsonPath("$.root.children[1].postCount").value(2))
                .andExpect(jsonPath("$.root.children[1].folderCount").value(1))
                .andExpect(jsonPath("$.root.children[1].children[0].displayPath").value("~/python/basic"));
    }

    @Test
    void othersTreeCountsOnlyReadablePosts() throws Exception {
        createIn(pythonId, "a.md", 644);
        createIn(pythonId, "b.md", 600);

        tree(leeToken, "kim")
                .andExpect(jsonPath("$.root.displayPath").value("/users/kim"))
                .andExpect(jsonPath("$.root.children[1].displayPath").value("/users/kim/python"))
                .andExpect(jsonPath("$.root.children[1].postCount").value(1));
        tree(leeToken, "nobody").andExpect(status().isNotFound());
    }

    @Test
    void folderListingForOwner() throws Exception {
        createIn(pythonId, "b.md", 600);
        createIn(pythonId, "a.md", 644);

        folder(kimToken, pythonId, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folder.displayPath").value("~/python"))
                .andExpect(jsonPath("$.folder.postCount").value(2))
                .andExpect(jsonPath("$.folder.folderCount").value(1))
                .andExpect(jsonPath("$.folder.owner.username").value("kim"))
                .andExpect(jsonPath("$.folder.editable").value(true))
                .andExpect(jsonPath("$.parent.id").value(kimHomeId))
                .andExpect(jsonPath("$.parent.displayPath").value("~"))
                .andExpect(jsonPath("$.folders[0].name").value("basic"))
                .andExpect(jsonPath("$.folders[0].displayPath").value("~/python/basic"))
                // 이름순
                .andExpect(jsonPath("$.posts.items[0].fileName").value("a.md"))
                .andExpect(jsonPath("$.posts.items[1].fileName").value("b.md"))
                .andExpect(jsonPath("$.posts.nextCursor").isEmpty());
    }

    @Test
    void folderListingForOthersHidesPrivatePosts() throws Exception {
        createIn(pythonId, "a.md", 644);
        createIn(pythonId, "b.md", 600);

        folder(leeToken, pythonId, "")
                .andExpect(jsonPath("$.folder.displayPath").value("/users/kim/python"))
                .andExpect(jsonPath("$.folder.postCount").value(1))
                .andExpect(jsonPath("$.folder.editable").value(false))
                .andExpect(jsonPath("$.parent.displayPath").value("/users/kim"))
                .andExpect(jsonPath("$.posts.items.length()").value(1))
                .andExpect(jsonPath("$.posts.items[0].fileName").value("a.md"));
    }

    @Test
    void homeHasNoParent() throws Exception {
        folder(kimToken, kimHomeId, "")
                .andExpect(jsonPath("$.folder.path").value(""))
                .andExpect(jsonPath("$.parent").isEmpty())
                .andExpect(jsonPath("$.folders[0].name").value("algo"))
                .andExpect(jsonPath("$.folders[1].name").value("python"));
    }

    @Test
    void sortByUpdated() throws Exception {
        Long a = createIn(pythonId, "a.md", 644);
        createIn(pythonId, "b.md", 644);

        // a를 고치면 a가 맨 위로 온다
        mockMvc.perform(patch("/api/v1/posts/" + a)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"고침\" }"))
                .andExpect(status().isOk());
        folder(kimToken, pythonId, "?sort=updated")
                .andExpect(jsonPath("$.posts.items[0].fileName").value("a.md"))
                .andExpect(jsonPath("$.posts.items[1].fileName").value("b.md"));

        // python은 algo보다 먼저 만들었지만 안의 글이 더 최근이라 앞에 온다. 이름순이면 algo가 먼저다
        folder(kimToken, kimHomeId, "?sort=updated")
                .andExpect(jsonPath("$.folders[0].name").value("python"))
                .andExpect(jsonPath("$.folders[1].name").value("algo"));
    }

    @Test
    void postsInFolderArePaged() throws Exception {
        createIn(pythonId, "a.md", 644);
        createIn(pythonId, "b.md", 644);
        createIn(pythonId, "c.md", 644);

        String body = folder(kimToken, pythonId, "?size=2")
                .andExpect(jsonPath("$.posts.items.length()").value(2))
                .andExpect(jsonPath("$.posts.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = body.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        folder(kimToken, pythonId, "?size=2&cursor=" + cursor)
                .andExpect(jsonPath("$.posts.items.length()").value(1))
                .andExpect(jsonPath("$.posts.items[0].fileName").value("c.md"))
                // 하위 폴더는 나눠 주지 않으므로 다음 페이지에도 그대로 있다
                .andExpect(jsonPath("$.folders.length()").value(1));

        // 이름순 커서를 최근 수정순에 보내면 400
        folder(kimToken, pythonId, "?sort=updated&cursor=" + cursor).andExpect(status().isBadRequest());
    }

    @Test
    void badFolderRequests() throws Exception {
        folder(kimToken, pythonId, "?sort=newest")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        folder(kimToken, 999_999L, "").andExpect(status().isNotFound());
    }

    @Test
    void fsResolvesFolders() throws Exception {
        fs(kimToken, "~")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("folder"))
                .andExpect(jsonPath("$.folder.id").value(kimHomeId))
                .andExpect(jsonPath("$.parent").isEmpty());
        // 끝의 /는 무시한다
        fs(kimToken, "~/python/")
                .andExpect(jsonPath("$.folder.id").value(pythonId))
                .andExpect(jsonPath("$.folders[0].name").value("basic"));
        fs(kimToken, "~/python/basic").andExpect(jsonPath("$.folder.id").value(basicId));
        // /users/{내 아이디}로 물어도 ~로 답한다
        fs(kimToken, "/users/kim/python").andExpect(jsonPath("$.folder.displayPath").value("~/python"));
        fs(leeToken, "/users/KIM/python").andExpect(jsonPath("$.folder.displayPath").value("/users/kim/python"));
    }

    @Test
    void fsResolvesPosts() throws Exception {
        Long id = createIn(pythonId, "a.md", 644);

        fs(kimToken, "~/python/a.md")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("post"))
                .andExpect(jsonPath("$.post.id").value(id))
                .andExpect(jsonPath("$.post.content").value("본문"))
                .andExpect(jsonPath("$.post.displayPath").value("~/python/a.md"));
        fs(leeToken, "/users/kim/python/a.md")
                .andExpect(jsonPath("$.post.editable").value(false));
    }

    @Test
    void fsHidesWhatYouCannotRead() throws Exception {
        createIn(pythonId, "secret.md", 600);
        Long trashed = createIn(pythonId, "old.md", 644);
        mockMvc.perform(delete("/api/v1/posts/" + trashed).header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken))
                .andExpect(status().isNoContent());

        // 비공개 글, 휴지통 글, 없는 글, 없는 폴더, 없는 사용자 모두 404다
        fs(leeToken, "/users/kim/python/secret.md").andExpect(status().isNotFound());
        fs(kimToken, "~/python/old.md").andExpect(status().isNotFound());
        fs(kimToken, "~/python/none.md").andExpect(status().isNotFound());
        fs(kimToken, "~/none").andExpect(status().isNotFound());
        fs(kimToken, "/users/nobody").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "python", "~kim", "~//python", "~/./python", "~/../lee", "~/.trash", "/users",
            "/users/", "/users//python" })
    void invalidPathsAreRejected(String path) throws Exception {
        fs(kimToken, path)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PATH"));
    }

    private Long createIn(Long folderId, String fileName, int mode) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"folderId\": %d, \"fileName\": \"%s\", \"content\": \"본문\", \"mode\": %d }"
                                .formatted(folderId, fileName, mode)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions tree(String token, String username) throws Exception {
        return mockMvc.perform(get("/api/v1/users/" + username + "/tree")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions folder(String token, Long folderId, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/folders/" + folderId + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions fs(String token, String path) throws Exception {
        return mockMvc.perform(get("/api/v1/fs").param("path", path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
