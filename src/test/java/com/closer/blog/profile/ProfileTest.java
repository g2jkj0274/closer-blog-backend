package com.closer.blog.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProfileTest {

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

    String parkToken;

    Long kimHomeId;

    Long parkHomeId;

    // kim: 공개 글 2, 비공개 글 1, 휴지통 글 1, 폴더 python. park: 공개 글 1. lee: 글 없음. 가입 순서 kim, lee, park
    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbcTemplate);
        User kim = authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
        User lee = authService.signup(new SignupRequest("lee", "lee@example.com", "password1", true, false));
        User park = authService.signup(new SignupRequest("park", "park@example.com", "password1", true, false));
        kimToken = jwtProvider.issue(kim.getId(), kim.getUsername()).value();
        leeToken = jwtProvider.issue(lee.getId(), lee.getUsername()).value();
        parkToken = jwtProvider.issue(park.getId(), park.getUsername()).value();
        kimHomeId = folderRepository.findByOwnerIdAndParentIsNull(kim.getId()).orElseThrow().getId();
        parkHomeId = folderRepository.findByOwnerIdAndParentIsNull(park.getId()).orElseThrow().getId();
        Long python = folderService.create(kim.getId(), kimHomeId, "python").getId();

        createPost(kimToken, python, "a.md", 644);
        createPost(kimToken, kimHomeId, "b.md", 644);
        createPost(kimToken, kimHomeId, "secret.md", 600);
        Long trashed = createPost(kimToken, kimHomeId, "old.md", 644);
        mockMvc.perform(delete("/api/v1/posts/" + trashed).header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken))
                .andExpect(status().isNoContent());
        createPost(parkToken, parkHomeId, "p.md", 644);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void me() throws Exception {
        fetch(kimToken, "/api/v1/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("kim"))
                .andExpect(jsonPath("$.displayName").value("kim"))
                .andExpect(jsonPath("$.email").value("kim@example.com"))
                .andExpect(jsonPath("$.bio").value(""))
                .andExpect(jsonPath("$.contact").value(""))
                .andExpect(jsonPath("$.joinedAt").isNotEmpty())
                // 휴지통을 뺀 전체 3, 그중 공개 2, 홈을 뺀 폴더 1
                .andExpect(jsonPath("$.postCount").value(3))
                .andExpect(jsonPath("$.publicPostCount").value(2))
                .andExpect(jsonPath("$.folderCount").value(1));
    }

    @Test
    void updateProfile() throws Exception {
        putProfile(kimToken, "{ \"displayName\": \"  김철수  \", \"bio\": \"포인터 그림\", \"contact\": \"github.com/kim\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("김철수"))
                .andExpect(jsonPath("$.bio").value("포인터 그림"))
                .andExpect(jsonPath("$.contact").value("github.com/kim"))
                .andExpect(jsonPath("$.postCount").value(3));

        // ""이면 비운다
        putProfile(kimToken, "{ \"displayName\": \"김철수\", \"bio\": \"\", \"contact\": \"\" }")
                .andExpect(jsonPath("$.bio").value(""));
        fetch(leeToken, "/api/v1/users/kim").andExpect(jsonPath("$.displayName").value("김철수"));
    }

    @Test
    void updateProfileRejectsBadInput() throws Exception {
        putProfile(kimToken, "{ \"displayName\": \"   \", \"bio\": \"\", \"contact\": \"\" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
        putProfile(kimToken, "{ \"displayName\": \"%s\", \"bio\": \"\", \"contact\": \"\" }".formatted("가".repeat(41)))
                .andExpect(status().isBadRequest());
        putProfile(kimToken, "{ \"displayName\": \"kim\", \"bio\": \"%s\", \"contact\": \"\" }".formatted("a".repeat(101)))
                .andExpect(jsonPath("$.errors[0].field").value("bio"));
        // 세 필드를 모두 보내야 한다
        putProfile(kimToken, "{ \"displayName\": \"kim\", \"bio\": \"\" }")
                .andExpect(jsonPath("$.errors[0].field").value("contact"));
    }

    @Test
    void summaryCountsTodaysPosts() throws Exception {
        fetch(kimToken, "/api/v1/me/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kim"))
                .andExpect(jsonPath("$.folderCount").value(1))
                .andExpect(jsonPath("$.postCount").value(3))
                .andExpect(jsonPath("$.todayPostCount").value(3));

        jdbcTemplate.update("UPDATE posts SET created_at = now() - interval '2 days' WHERE file_name = 'a.md'");
        fetch(kimToken, "/api/v1/me/summary").andExpect(jsonPath("$.todayPostCount").value(2));
    }

    @Test
    void profileCountsWhatViewerCanRead() throws Exception {
        fetch(leeToken, "/api/v1/users/kim")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kim"))
                .andExpect(jsonPath("$.postCount").value(2))
                .andExpect(jsonPath("$.folderCount").value(1))
                .andExpect(jsonPath("$.isMe").value(false))
                // 이메일과 연락처는 남에게 주지 않는다
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.contact").doesNotExist());
        fetch(kimToken, "/api/v1/users/KIM")
                .andExpect(jsonPath("$.postCount").value(3))
                .andExpect(jsonPath("$.isMe").value(true));
        fetch(kimToken, "/api/v1/users/nobody").andExpect(status().isNotFound());
    }

    @Test
    void usersSortedByPublicPosts() throws Exception {
        // kim 2, park 1, lee 0. kim이 봐도 kim의 비공개 글은 세지 않는다
        fetch(kimToken, "/api/v1/users")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].username").value("kim"))
                .andExpect(jsonPath("$.items[0].postCount").value(2))
                .andExpect(jsonPath("$.items[0].folderCount").value(1))
                .andExpect(jsonPath("$.items[1].username").value("park"))
                .andExpect(jsonPath("$.items[2].username").value("lee"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void usersSortedByJoined() throws Exception {
        fetch(kimToken, "/api/v1/users?sort=joined")
                .andExpect(jsonPath("$.items[0].username").value("park"))
                .andExpect(jsonPath("$.items[1].username").value("lee"))
                .andExpect(jsonPath("$.items[2].username").value("kim"));
    }

    @Test
    void usersArePaged() throws Exception {
        String body = fetch(kimToken, "/api/v1/users?size=2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = body.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        fetch(kimToken, "/api/v1/users?size=2&cursor=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].username").value("lee"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        // 글 수 순 커서를 가입순에 보내면 400
        fetch(kimToken, "/api/v1/users?sort=joined&cursor=" + cursor).andExpect(status().isBadRequest());
    }

    @Test
    void findUsersByUsernameOrDisplayName() throws Exception {
        putProfile(leeToken, "{ \"displayName\": \"이영희\", \"bio\": \"\", \"contact\": \"\" }");

        fetch(kimToken, "/api/v1/users?q=KI")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].username").value("kim"));
        fetch(kimToken, "/api/v1/users?q=영희")
                .andExpect(jsonPath("$.items[0].username").value("lee"));
        // %는 글자로 찾는다. 모두 걸리지 않는다
        fetch(kimToken, "/api/v1/users?q=%25").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void badUserListParams() throws Exception {
        fetch(kimToken, "/api/v1/users?q=" + "a".repeat(21)).andExpect(status().isBadRequest());
        fetch(kimToken, "/api/v1/users?sort=name").andExpect(status().isBadRequest());
        fetch(kimToken, "/api/v1/users?size=51").andExpect(status().isBadRequest());
    }

    private Long createPost(String token, Long folderId, String fileName, int mode) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"folderId\": %d, \"fileName\": \"%s\", \"mode\": %d }"
                                .formatted(folderId, fileName, mode)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions fetch(String token, String url) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions putProfile(String token, String body) throws Exception {
        return mockMvc.perform(put("/api/v1/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
