package com.closer.blog.post;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class PostTest {

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

    Long leeHomeId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        User kim = authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
        User lee = authService.signup(new SignupRequest("lee", "lee@example.com", "password1", true, false));
        kimToken = jwtProvider.issue(kim.getId(), kim.getUsername()).value();
        leeToken = jwtProvider.issue(lee.getId(), lee.getUsername()).value();
        kimHomeId = homeIdOf(kim);
        leeHomeId = homeIdOf(lee);
        kimPythonId = folderService.create(kim.getId(), kimHomeId, "python").getId();
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void createPost() throws Exception {
        create(kimToken, """                                                                                       
                  { "folderId": %d, "fileName": "pointer.md", "title": "포인터", "content": "# 포인터\\n\\n가 나",
                    "tags": ["#포인터", "예제", "예제"] }
                  """.formatted(kimPythonId))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/posts/")))
                .andExpect(jsonPath("$.path").value("python/pointer.md"))
                .andExpect(jsonPath("$.displayPath").value("~/python/pointer.md"))
                .andExpect(jsonPath("$.tags[0]").value("포인터"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.mode").value(644))
                .andExpect(jsonPath("$.owner.username").value("kim"))
                .andExpect(jsonPath("$.folder.displayPath").value("~/python"))
                .andExpect(jsonPath("$.charCount").value(6))
                .andExpect(jsonPath("$.readingMinutes").value(1))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void onlyFolderAndFileNameAreRequired() throws Exception {
        create(kimToken, "{ \"folderId\": %d, \"fileName\": \"empty.md\" }".formatted(kimHomeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("empty.md"))
                .andExpect(jsonPath("$.title").value(""))
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.tags.length()").value(0))
                .andExpect(jsonPath("$.readingMinutes").value(0));
    }

    @Test
    void createRejectsBadInput() throws Exception {
        create(kimToken, "{ \"folderId\": %d, \"fileName\": \"notes.txt\" }".formatted(kimHomeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fileName"));
        create(kimToken, "{ \"folderId\": %d, \"fileName\": \"a.md\", \"mode\": 640 }".formatted(kimHomeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(kimToken, "{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(leeHomeId))
                .andExpect(status().isForbidden());
        create(kimToken, "{ \"folderId\": 999999, \"fileName\": \"a.md\" }")
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateFileNameIsRejected() throws Exception {
        createIn(kimHomeId, "a.md");
        create(kimToken, "{ \"folderId\": %d, \"fileName\": \"a.md\" }".formatted(kimHomeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NAME_TAKEN"));
    }

    @Test
    void othersSeePublicPostWithTheirDisplayPath() throws Exception {
        Long id = createIn(kimPythonId, "pointer.md");

        read(leeToken, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayPath").value("/users/kim/python/pointer.md"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void privatePostIsHiddenFromOthers() throws Exception {
        Long id = createIn(kimHomeId, "secret.md");
        update(kimToken, id, "{ \"mode\": 600 }").andExpect(status().isOk());

        read(kimToken, id).andExpect(status().isOk());
        read(leeToken, id).andExpect(status().isNotFound());
        // 고치기도 403이 아니라 404다. 있다는 사실을 숨긴다
        update(leeToken, id, "{ \"title\": \"x\" }").andExpect(status().isNotFound());
    }

    @Test
    void othersCannotEditPublicPost() throws Exception {
        Long id = createIn(kimHomeId, "a.md");
        update(leeToken, id, "{ \"title\": \"x\" }")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void saveWithVersion() throws Exception {
        Long id = createIn(kimHomeId, "a.md");

        update(kimToken, id, "{ \"content\": \"첫 수정\", \"version\": 0 }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("첫 수정"))
                .andExpect(jsonPath("$.version").value(1));

        // 다른 탭에서 version 0으로 저장하면 거절한다
        update(kimToken, id, "{ \"content\": \"옛 탭\", \"version\": 0 }")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_VERSION_CONFLICT"));
    }

    @Test
    void sameValuesDoNotBumpVersion() throws Exception {
        Long id = createIn(kimHomeId, "a.md");

        update(kimToken, id, "{ \"title\": \"\", \"tags\": [], \"mode\": 644 }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void replaceTagsKeepsOrder() throws Exception {
        Long id = createIn(kimHomeId, "a.md");
        update(kimToken, id, "{ \"tags\": [\"a\", \"b\", \"c\"] }").andExpect(status().isOk());

        update(kimToken, id, "{ \"tags\": [\"c\", \"d\", \"A\"] }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("c"))
                .andExpect(jsonPath("$.tags[1]").value("d"))
                // 대소문자만 다르면 이미 있는 태그 이름을 쓴다
                .andExpect(jsonPath("$.tags[2]").value("a"))
                .andExpect(jsonPath("$.version").value(2));

        update(kimToken, id, "{ \"tags\": [] }")
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void renameAndMove() throws Exception {
        Long id = createIn(kimHomeId, "a.md");
        createIn(kimPythonId, "taken.md");

        update(kimToken, id, "{ \"fileName\": \"b.md\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("b.md"));
        update(kimToken, id, "{ \"folderId\": %d, \"fileName\": \"taken.md\" }".formatted(kimPythonId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NAME_TAKEN"));
        update(kimToken, id, "{ \"folderId\": %d }".formatted(kimPythonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("python/b.md"))
                .andExpect(jsonPath("$.folder.id").value(kimPythonId));
        update(kimToken, id, "{ \"folderId\": %d }".formatted(leeHomeId))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/posts/1")).andExpect(status().isUnauthorized());
    }

    private ResultActions create(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long createIn(Long folderId, String fileName) throws Exception {
        String body = create(kimToken, "{ \"folderId\": %d, \"fileName\": \"%s\" }".formatted(folderId, fileName))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions read(String token, Long id) throws Exception {
        return mockMvc.perform(get("/api/v1/posts/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions update(String token, Long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/posts/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long homeIdOf(User user) {
        return folderRepository.findByOwnerIdAndParentIsNull(user.getId()).orElseThrow().getId();
    }
}