package com.closer.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MVP 완료 기준 시나리오(docs/mvp-scope.md 4절)를 API로 처음부터 끝까지 따라간다.
 * 각 단계가 부르는 API는 docs/api-spec.md 11절의 표와 같다.
 * <p>
 * 앞 단계가 만든 것(토큰, 폴더, 글)을 다음 단계가 이어 쓰므로 테스트 하나에 단계를 순서대로 둔다.
 * 단계별 세부 규칙은 기능별 테스트(PostTest, FsTest, SearchTest 등)가 확인하고, 여기서는 흐름이 이어지는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MvpScenarioTest {

    private static final String REFRESH_COOKIE = "refresh_token";

    // 코드 블록과 인용이 든 마크다운. 렌더링은 프런트가 하고 서버는 원문 그대로 저장한다
    private static final String POINTER_CONTENT =
            "# 포인터\\n\\n> 주소를 담는 변수\\n\\n```c\\nint *p = &x;\\n```\\n\\n포인터는 그림으로 보면 쉽다.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // 단계 사이에 이어 쓰는 상태
    String kimToken;

    String kimRefreshCookie;

    String leeToken;

    Long kimHomeId;

    Long pythonId;

    Long postId;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void mvpScenario() throws Exception {
        guestIsSentToLogin();                  // 1
        signupLoginAndHome();                  // 2
        mkdirCdTouchAndSave();                 // 3
        catReadsThePost();                     // 4
        viThenMv();                            // 5
        chmod600HidesThePostFromOthers();      // 6
        othersReadPublicPostsButCannotEdit();  // 7
        grepAndFind();                         // 8
        rmUndoAndRestoreFromTrash();           // 9
        rmdirIsRefused();                      // 10
        typoIsNotFound();                      // 11
        logoutEndsTheSession();                // 12
    }

    // 1. 게스트가 다른 주소를 열면 로그인 화면으로 간다. 프런트는 앱을 켤 때 refresh를 부르고 401이면 로그인으로 보낸다
    private void guestIsSentToLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/fs").param("path", "~"))
                .andExpect(status().isUnauthorized());
    }

    // 2. 약관에 동의하고 가입한 뒤 로그인하면 홈이 나온다
    private void signupLoginAndHome() throws Exception {
        // 약관에 동의하지 않으면 가입되지 않는다
        send(post("/api/v1/auth/signup"), null,
                "{ \"username\": \"kim\", \"email\": \"kim@example.com\", \"password\": \"password1\" }")
                .andExpect(status().isBadRequest());
        signup("kim");
        signup("lee");

        ResultActions login = login("kim");
        kimToken = JsonPath.read(body(login), "$.accessToken");
        kimRefreshCookie = login.andReturn().getResponse().getCookie(REFRESH_COOKIE).getValue();
        leeToken = JsonPath.read(body(login("lee")), "$.accessToken");

        // 홈: neofetch 줄과 왼쪽 트리
        send(get("/api/v1/me/summary"), kimToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("kim"))
                .andExpect(jsonPath("$.postCount").value(0));
        ResultActions tree = send(get("/api/v1/users/kim/tree"), kimToken, null)
                .andExpect(jsonPath("$.root.displayPath").value("~"))
                .andExpect(jsonPath("$.root.children.length()").value(0));
        kimHomeId = readLong(tree, "$.root.id");
    }

    // 3. mkdir python → cd python → touch pointer.md로 글을 쓰고 태그를 달아 저장한다
    private void mkdirCdTouchAndSave() throws Exception {
        ResultActions mkdir = send(post("/api/v1/folders"), kimToken,
                "{ \"parentId\": %d, \"name\": \"python\" }".formatted(kimHomeId))
                .andExpect(status().isCreated());
        pythonId = readLong(mkdir, "$.id");

        send(get("/api/v1/fs").param("path", "~/python"), kimToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("folder"))
                .andExpect(jsonPath("$.folder.id").value(pythonId))
                .andExpect(jsonPath("$.posts.items.length()").value(0));

        // touch는 서버를 부르지 않는다. 편집기의 첫 :w가 글을 만든다
        ResultActions save = send(post("/api/v1/posts"), kimToken, """
                { "folderId": %d, "fileName": "pointer.md", "title": "포인터를 그림으로", "content": "%s",
                  "tags": ["포인터", "C"] }
                """.formatted(pythonId, POINTER_CONTENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
        postId = readLong(save, "$.id");
    }

    // 4. cat pointer.md로 읽는다
    private void catReadsThePost() throws Exception {
        send(get("/api/v1/fs").param("path", "~/python/pointer.md"), kimToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("post"))
                .andExpect(jsonPath("$.post.id").value(postId))
                .andExpect(jsonPath("$.post.content").value(
                        "# 포인터\n\n> 주소를 담는 변수\n\n```c\nint *p = &x;\n```\n\n포인터는 그림으로 보면 쉽다."))
                .andExpect(jsonPath("$.post.tags[0]").value("포인터"))
                .andExpect(jsonPath("$.post.tags[1]").value("C"))
                .andExpect(jsonPath("$.post.editable").value(true));
    }

    // 5. vi pointer.md로 고치고, mv pointer.md ptr.md로 이름을 바꾼다
    private void viThenMv() throws Exception {
        // 편집기의 :w는 받아 둔 version을 함께 보낸다
        send(patch("/api/v1/posts/" + postId), kimToken,
                "{ \"content\": \"%s\\n\\n포인터 연산도 정리했다.\", \"version\": 0 }".formatted(POINTER_CONTENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // 다른 탭에 열려 있던 옛 version으로 저장하면 거절한다
        send(patch("/api/v1/posts/" + postId), kimToken, "{ \"content\": \"옛 탭\", \"version\": 0 }")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_VERSION_CONFLICT"));

        send(patch("/api/v1/posts/" + postId), kimToken, "{ \"fileName\": \"ptr.md\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("python/ptr.md"));
        send(get("/api/v1/fs").param("path", "~/python/pointer.md"), kimToken, null)
                .andExpect(status().isNotFound());
        send(get("/api/v1/fs").param("path", "~/python/ptr.md"), kimToken, null)
                .andExpect(status().isOk());
    }

    // 6. chmod 600 ptr.md로 비공개로 바꾼다. 다른 회원에게 폴더는 빈 폴더로, 글 주소는 없는 경로로 보이고
    //    grep과 find에도 나오지 않는다
    private void chmod600HidesThePostFromOthers() throws Exception {
        send(patch("/api/v1/posts/" + postId), kimToken, "{ \"mode\": 600 }")
                .andExpect(jsonPath("$.mode").value(600));

        send(get("/api/v1/fs").param("path", "/users/kim/python"), leeToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folder.postCount").value(0))
                .andExpect(jsonPath("$.posts.items.length()").value(0));
        send(get("/api/v1/fs").param("path", "/users/kim/python/ptr.md"), leeToken, null)
                .andExpect(status().isNotFound());
        send(get("/api/v1/search/content").param("q", "포인터"), leeToken, null)
                .andExpect(jsonPath("$.fileCount").value(0));
        send(get("/api/v1/search/names").param("q", "ptr"), leeToken, null)
                .andExpect(jsonPath("$.posts.length()").value(0));
        send(get("/api/v1/users/kim"), leeToken, null)
                .andExpect(jsonPath("$.postCount").value(0));
        // 본인에게는 그대로 보인다
        send(get("/api/v1/search/content").param("q", "포인터"), kimToken, null)
                .andExpect(jsonPath("$.fileCount").value(1));

        // 다음 단계에서 다른 회원이 읽도록 다시 공개한다
        send(patch("/api/v1/posts/" + postId), kimToken, "{ \"mode\": 644 }")
                .andExpect(jsonPath("$.mode").value(644));
    }

    // 7. 다른 회원이 ls /users → cd /users/kim으로 들어가 공개 글을 읽는다. 고치거나 지울 수는 없다
    private void othersReadPublicPostsButCannotEdit() throws Exception {
        send(get("/api/v1/users"), leeToken, null)
                .andExpect(jsonPath("$.items[0].username").value("kim"))
                .andExpect(jsonPath("$.items[0].postCount").value(1));
        send(get("/api/v1/users/kim"), leeToken, null)
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.isMe").value(false));
        send(get("/api/v1/users/kim/posts"), leeToken, null)
                .andExpect(jsonPath("$.items[0].displayPath").value("/users/kim/python/ptr.md"));
        send(get("/api/v1/users/kim/tree"), leeToken, null)
                .andExpect(jsonPath("$.root.children[0].name").value("python"));
        send(get("/api/v1/fs").param("path", "/users/kim/python/ptr.md"), leeToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.editable").value(false));

        send(patch("/api/v1/posts/" + postId), leeToken, "{ \"title\": \"남이 고침\" }")
                .andExpect(status().isForbidden());
        send(delete("/api/v1/posts/" + postId), leeToken, null)
                .andExpect(status().isForbidden());
    }

    // 8. grep 포인터가 일치한 줄을 보여 주고, find가 파일을 찾는다.
    //    5단계에서 이름을 ptr.md로 바꿨으므로 find는 ptr로 찾는다
    private void grepAndFind() throws Exception {
        ResultActions grep = send(get("/api/v1/search/content").param("q", "포인터"), leeToken, null)
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.items[0].post.fileName").value("ptr.md"))
                .andExpect(jsonPath("$.items[0].lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.items[0].lines[0].text").value("# 포인터"));
        assertThat(readLong(grep, "$.items[0].matchCount")).isGreaterThanOrEqualTo(3);

        send(get("/api/v1/search/names").param("q", "ptr"), leeToken, null)
                .andExpect(jsonPath("$.posts[0].id").value(postId));
        send(get("/api/v1/search/names").param("q", "pyth"), leeToken, null)
                .andExpect(jsonPath("$.folders[0].displayPath").value("/users/kim/python"));
    }

    // 9. rm ptr.md → u로 되돌린다 → 다시 지운 뒤 ~/.trash에서 복구한다.
    //    파일 이름을 입력하는 확인 창은 프런트가 띄운다
    private void rmUndoAndRestoreFromTrash() throws Exception {
        send(delete("/api/v1/posts/" + postId), kimToken, null).andExpect(status().isNoContent());
        send(get("/api/v1/fs").param("path", "~/python/ptr.md"), kimToken, null).andExpect(status().isNotFound());

        // u: 프런트가 기억해 둔 id로 복구한다. 본문 없이 보낸다
        send(post("/api/v1/trash/" + postId + "/restore"), kimToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("python/ptr.md"));

        send(delete("/api/v1/posts/" + postId), kimToken, null).andExpect(status().isNoContent());
        send(get("/api/v1/trash"), kimToken, null)
                .andExpect(jsonPath("$.items[0].id").value(postId))
                .andExpect(jsonPath("$.items[0].originalDisplayPath").value("~/python/ptr.md"));
        send(post("/api/v1/trash/" + postId + "/restore"), kimToken, null).andExpect(status().isOk());
        send(get("/api/v1/trash"), kimToken, null).andExpect(jsonPath("$.items.length()").value(0));
        send(get("/api/v1/fs").param("path", "~/python/ptr.md"), kimToken, null).andExpect(status().isOk());
    }

    // 10. 글이 남은 폴더에 rmdir을 하면 거절된다
    private void rmdirIsRefused() throws Exception {
        send(delete("/api/v1/folders/" + pythonId), kimToken, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDER_NOT_EMPTY"));
    }

    // 11. cat pointr.md 같은 오타는 없는 경로다
    private void typoIsNotFound() throws Exception {
        send(get("/api/v1/fs").param("path", "~/python/pointr.md"), kimToken, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(get("/api/v1/fs").param("path", "~/pyhton"), kimToken, null)
                .andExpect(status().isNotFound());
    }

    // 12. logout 후에는 다시 로그인으로 간다. 리프레시 토큰이 지워져 refresh가 401이 된다.
    //     액세스 토큰은 서버에 저장하지 않으므로 프런트가 메모리에서 버린다 (docs/api-spec.md 1.2절)
    private void logoutEndsTheSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(REFRESH_COOKIE, kimRefreshCookie)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, kimRefreshCookie)))
                .andExpect(status().isUnauthorized());
    }

    private void signup(String username) throws Exception {
        send(post("/api/v1/auth/signup"), null, """
                { "username": "%s", "email": "%s@example.com", "password": "password1", "termsAgreed": true }
                """.formatted(username, username))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String username) throws Exception {
        return send(post("/api/v1/auth/login"), null,
                "{ \"login\": \"%s\", \"password\": \"password1\" }".formatted(username))
                .andExpect(status().isOk());
    }

    // token이 null이면 로그인하지 않은 요청, body가 null이면 본문 없는 요청이다
    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long readLong(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(body(result), path)).longValue();
    }
}
