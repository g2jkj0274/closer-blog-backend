package com.closer.blog.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SearchTest {

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

    Long leeHomeId;

    Long pointerPostId;

    /*
     * kim: ~/python/pointer.md(공개, #포인터 #예제), ~/notes.md(공개, #예제), ~/secret.md(비공개, #포인터 #비밀)
     * lee: ~/pointer 폴더, ~/my-pointer.md(공개)
     * 만든 순서대로 고친 시각이 뒤다
     */
    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbcTemplate);
        User kim = authService.signup(new SignupRequest("kim", "kim@example.com", "password1", true, false));
        User lee = authService.signup(new SignupRequest("lee", "lee@example.com", "password1", true, false));
        kimToken = jwtProvider.issue(kim.getId(), kim.getUsername()).value();
        leeToken = jwtProvider.issue(lee.getId(), lee.getUsername()).value();
        kimHomeId = folderRepository.findByOwnerIdAndParentIsNull(kim.getId()).orElseThrow().getId();
        leeHomeId = folderRepository.findByOwnerIdAndParentIsNull(lee.getId()).orElseThrow().getId();
        pythonId = folderService.create(kim.getId(), kimHomeId, "python").getId();
        folderService.create(lee.getId(), leeHomeId, "pointer");

        pointerPostId = createPost(kimToken, pythonId, "pointer.md", 644,
                "# 포인터\\n\\n포인터는 주소다.\\n그림으로 포인터를 다시 그려 봤다. 포인터!", "\"포인터\", \"예제\"");
        createPost(kimToken, kimHomeId, "notes.md", 644, "아무 내용", "\"예제\"");
        createPost(kimToken, kimHomeId, "secret.md", 600, "비밀 포인터", "\"포인터\", \"비밀\"");
        createPost(leeToken, leeHomeId, "my-pointer.md", 644, "", "");
    }

    @AfterEach
    void tearDown() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void grepFindsLinesAndRanges() throws Exception {
        fetch(leeToken, "/api/v1/search/content?q=포인터")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.items[0].post.id").value(pointerPostId))
                .andExpect(jsonPath("$.items[0].post.displayPath").value("/users/kim/python/pointer.md"))
                .andExpect(jsonPath("$.items[0].matchCount").value(4))
                .andExpect(jsonPath("$.items[0].lines.length()").value(3))
                .andExpect(jsonPath("$.items[0].lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.items[0].lines[0].text").value("# 포인터"))
                .andExpect(jsonPath("$.items[0].lines[0].ranges[0][0]").value(2))
                .andExpect(jsonPath("$.items[0].lines[0].ranges[0][1]").value(5))
                .andExpect(jsonPath("$.items[0].lines[1].lineNo").value(3))
                .andExpect(jsonPath("$.items[0].lines[2].lineNo").value(4))
                .andExpect(jsonPath("$.items[0].lines[2].ranges.length()").value(2))
                .andExpect(jsonPath("$.items[0].lines[2].ranges[1][0]").value(20))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void grepShowsOwnPrivatePostsNewestFirst() throws Exception {
        fetch(kimToken, "/api/v1/search/content?q=포인터")
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.items[0].post.fileName").value("secret.md"))
                .andExpect(jsonPath("$.items[1].post.fileName").value("pointer.md"));
        fetch(leeToken, "/api/v1/search/content?q=포인터&scope=mine")
                .andExpect(jsonPath("$.fileCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void grepIgnoresCaseAndTreatsPercentAsText() throws Exception {
        createPost(leeToken, leeHomeId, "en.md", 644, "Pointer and POINTER", "");
        createPost(leeToken, leeHomeId, "sale.md", 644, "50% 할인", "");
        // 이스케이프하지 않으면 50%가 "50 뒤에 아무거나"가 되어 이 글도 걸린다
        createPost(leeToken, leeHomeId, "apple.md", 644, "사과 50개", "");

        fetch(leeToken, "/api/v1/search/content?q=pointer")
                .andExpect(jsonPath("$.items[0].post.fileName").value("en.md"))
                .andExpect(jsonPath("$.items[0].matchCount").value(2));
        // URL에 %를 직접 쓰면 MockMvc가 다시 인코딩하므로 param()으로 보낸다
        fetchWithQ(leeToken, "/api/v1/search/content", "50%")
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.items[0].post.fileName").value("sale.md"));
    }

    @Test
    void grepWithTwoKoreanLetters() throws Exception {
        fetch(kimToken, "/api/v1/search/content?q=포인").andExpect(jsonPath("$.fileCount").value(2));
    }

    @Test
    void grepSkipsTrashedPosts() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/" + pointerPostId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + kimToken))
                .andExpect(status().isNoContent());

        fetch(leeToken, "/api/v1/search/content?q=포인터").andExpect(jsonPath("$.fileCount").value(0));
    }

    @Test
    void grepIsPaged() throws Exception {
        String body = fetch(kimToken, "/api/v1/search/content?q=포인터&size=1")
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = body.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        // fileCount는 첫 페이지에만 있다
        fetch(kimToken, "/api/v1/search/content?q=포인터&size=1&cursor=" + cursor)
                .andExpect(jsonPath("$.fileCount").isEmpty())
                .andExpect(jsonPath("$.items[0].post.fileName").value("pointer.md"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void grepRejectsBadQueries() throws Exception {
        fetch(kimToken, "/api/v1/search/content?q=포").andExpect(status().isBadRequest());
        // 앞뒤 공백을 자르면 한 글자다
        fetchWithQ(kimToken, "/api/v1/search/content", " 포 ").andExpect(status().isBadRequest());
        fetch(kimToken, "/api/v1/search/content?q=" + "a".repeat(101)).andExpect(status().isBadRequest());
        fetch(kimToken, "/api/v1/search/content?q=포인터&scope=everyone").andExpect(status().isBadRequest());
        fetch(kimToken, "/api/v1/search/content").andExpect(status().isBadRequest());
    }

    @Test
    void findPutsPrefixMatchesFirst() throws Exception {
        fetch(kimToken, "/api/v1/search/names?q=pointer")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folders.length()").value(1))
                .andExpect(jsonPath("$.folders[0].name").value("pointer"))
                .andExpect(jsonPath("$.folders[0].displayPath").value("/users/lee/pointer"))
                .andExpect(jsonPath("$.folders[0].owner.username").value("lee"))
                // my-pointer.md가 더 최근이지만 이름이 pointer로 시작하는 쪽이 먼저다
                .andExpect(jsonPath("$.posts[0].fileName").value("pointer.md"))
                .andExpect(jsonPath("$.posts[1].fileName").value("my-pointer.md"));
    }

    @Test
    void findSearchesFolderPathsAndHidesPrivatePosts() throws Exception {
        fetch(leeToken, "/api/v1/search/names?q=pyth")
                .andExpect(jsonPath("$.folders[0].displayPath").value("/users/kim/python"))
                .andExpect(jsonPath("$.posts[0].fileName").value("pointer.md"));
        fetch(leeToken, "/api/v1/search/names?q=pyth&scope=mine")
                .andExpect(jsonPath("$.folders.length()").value(0))
                .andExpect(jsonPath("$.posts.length()").value(0));
        fetch(leeToken, "/api/v1/search/names?q=secret").andExpect(jsonPath("$.posts.length()").value(0));
        fetch(kimToken, "/api/v1/search/names?q=secret").andExpect(jsonPath("$.posts[0].fileName").value("secret.md"));
    }

    @Test
    void tagSuggestionsCountReadablePosts() throws Exception {
        fetch(kimToken, "/api/v1/tags?q=포")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("포인터"))
                .andExpect(jsonPath("$.items[0].postCount").value(2));
        fetch(leeToken, "/api/v1/tags?q=포").andExpect(jsonPath("$.items[0].postCount").value(1));
        // 앞의 #은 뗀다
        fetchWithQ(leeToken, "/api/v1/tags", "#예").andExpect(jsonPath("$.items[0].name").value("예제"));
        // 읽을 수 있는 글이 없는 태그는 빠진다
        fetch(leeToken, "/api/v1/tags?q=비밀").andExpect(jsonPath("$.items.length()").value(0));
        fetch(kimToken, "/api/v1/tags?q=비밀").andExpect(jsonPath("$.items[0].postCount").value(1));
    }

    @Test
    void tagSuggestionsAreSortedByPostCount() throws Exception {
        createPost(leeToken, leeHomeId, "s1.md", 644, "", "\"Spring\"");
        createPost(leeToken, leeHomeId, "s2.md", 644, "", "\"spa\"");
        createPost(leeToken, leeHomeId, "s3.md", 644, "", "\"spa\"");

        fetch(leeToken, "/api/v1/tags?q=SP")
                .andExpect(jsonPath("$.items[0].name").value("spa"))
                .andExpect(jsonPath("$.items[1].name").value("Spring"));
        fetch(leeToken, "/api/v1/tags?q=sp&size=21").andExpect(status().isBadRequest());
    }

    @Test
    void postsByTag() throws Exception {
        fetch(kimToken, "/api/v1/tags/포인터/posts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag.name").value("포인터"))
                .andExpect(jsonPath("$.items[0].fileName").value("secret.md"))
                .andExpect(jsonPath("$.items[1].fileName").value("pointer.md"));
        fetch(leeToken, "/api/v1/tags/포인터/posts").andExpect(jsonPath("$.items.length()").value(1));
        fetch(leeToken, "/api/v1/tags/포인터/posts?scope=mine").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void postsByTagIgnoresCaseAndUnknownTagIsEmpty() throws Exception {
        createPost(leeToken, leeHomeId, "s1.md", 644, "", "\"Spring\"");

        // 저장된 표기로 돌려준다
        fetch(kimToken, "/api/v1/tags/SPRING/posts")
                .andExpect(jsonPath("$.tag.name").value("Spring"))
                .andExpect(jsonPath("$.items.length()").value(1));
        fetch(kimToken, "/api/v1/tags/없는태그/posts")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag.name").value("없는태그"))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void postsByTagArePaged() throws Exception {
        String body = fetch(kimToken, "/api/v1/tags/포인터/posts?size=1")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String cursor = body.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        fetch(kimToken, "/api/v1/tags/포인터/posts?size=1&cursor=" + cursor)
                .andExpect(jsonPath("$.items[0].fileName").value("pointer.md"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    /**
     * DB 설계 5.3절: Testcontainers의 DB에서 한글 검색어가 pg_trgm 인덱스를 쓸 수 있는지 EXPLAIN으로 확인한다.
     * 테스트 데이터가 적으면 순차 조회가 더 싸므로 enable_seqscan을 끄고 계획을 본다.
     */
    @Test
    void koreanSearchCanUseTrigramIndexes() {
        // PostgreSQL 16부터 lc_ctype은 설정이 아니라 데이터베이스의 속성이다
        assertThat(jdbcTemplate.queryForObject(
                "SELECT datctype FROM pg_database WHERE datname = current_database()", String.class))
                .containsIgnoringCase("utf");
        assertThat(explain("SELECT id FROM posts p WHERE p.content ILIKE '%포인터%'"))
                .contains("ix_posts_content_trgm");
        assertThat(explain("SELECT id FROM posts p WHERE p.file_name ILIKE '%포인터%'"))
                .contains("ix_posts_file_name_trgm");
        assertThat(explain("SELECT id FROM folders f WHERE f.path ILIKE '%자료구조%'"))
                .contains("ix_folders_path_trgm");
    }

    private String explain(String sql) {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                List<String> plan = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
                    while (rs.next()) {
                        plan.add(rs.getString(1));
                    }
                }
                finally {
                    statement.execute("RESET enable_seqscan");
                }
                return String.join("\n", plan);
            }
        });
    }

    private Long createPost(String token, Long folderId, String fileName, int mode, String content, String tags)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"folderId\": %d, \"fileName\": \"%s\", \"mode\": %d, \"content\": \"%s\", \"tags\": [%s] }"
                                .formatted(folderId, fileName, mode, content, tags)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private ResultActions fetch(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions fetchWithQ(String token, String path, String q) throws Exception {
        return mockMvc.perform(get(path).param("q", q).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
