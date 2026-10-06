package com.closer.blog.search.service;

import java.util.List;
import java.util.Optional;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.common.web.CursorCodec;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.common.web.DisplayPath;
import com.closer.blog.post.domain.Post;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.PostSummary;
import com.closer.blog.post.service.PostService;
import com.closer.blog.search.dto.ContentSearchResult;
import com.closer.blog.search.dto.NameSearchResult;
import com.closer.blog.search.dto.TagPosts;
import com.closer.blog.search.dto.TagSuggestions;
import com.closer.blog.search.repository.SearchQueryRepository;
import com.closer.blog.tag.domain.Tag;
import com.closer.blog.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * grep, find, 태그 조회. 글 id는 SearchQueryRepository가 찾고, 글 내용은 post 패키지에서 읽는다.
 * PostSummary가 지연 로딩 연관을 읽으므로 모든 메서드가 읽기 전용 트랜잭션 안에서 돈다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    // find는 폴더와 글 각각 이만큼만 준다 (docs/api-spec.md 9절)
    static final int MAX_NAME_RESULTS = 50;

    private final SearchQueryRepository queryRepository;

    private final PostService postService;

    private final TagService tagService;

    /**
     * grep. 본문에 검색어가 들어 있는 글을 최근 수정순으로. 페이지마다 그 페이지 글의 본문만 읽는다.
     */
    public ContentSearchResult searchContent(Long viewerId, String rawQ, boolean mineOnly, String cursor, int size) {
        String q = query(rawQ, 2, 100);
        CursorCodec.Cursor after = (cursor == null) ? null : CursorCodec.decode(cursor);
        List<Long> ids = queryRepository.findPostIdsByContent(q, viewerId, mineOnly, after, size + 1);
        List<Post> rows = postService.findReadableInOrder(ids, viewerId);

        CursorPage<ContentSearchResult.Hit> page = CursorPage.of(rows, size, post -> {
            LineMatcher.Result matched = LineMatcher.match(post.getContent(), q);
            return new ContentSearchResult.Hit(PostSummary.of(post, viewerId), matched.matchCount(), matched.lines());
        }, this::updatedCursor);
        // 일치한 글 수는 첫 페이지에서만 센다
        Long fileCount = (after == null) ? queryRepository.countPostsByContent(q, viewerId, mineOnly) : null;
        return new ContentSearchResult(fileCount, page.items(), page.nextCursor());
    }

    /**
     * find. 파일 이름, 폴더 이름, 경로에서 찾는다.
     */
    public NameSearchResult searchNames(Long viewerId, String rawQ, boolean mineOnly) {
        String q = query(rawQ, 1, 100);
        List<NameSearchResult.FolderHit> folders = queryRepository
                .findFoldersByName(q, viewerId, mineOnly, MAX_NAME_RESULTS).stream()
                .map(row -> new NameSearchResult.FolderHit(row.id(), row.name(),
                        DisplayPath.of(row.ownerId().equals(viewerId), row.ownerUsername(), row.path()),
                        new PostDetail.UserRef(row.ownerUsername(), row.ownerDisplayName())))
                .toList();
        List<Long> postIds = queryRepository.findPostIdsByName(q, viewerId, mineOnly, MAX_NAME_RESULTS);
        List<PostSummary> posts = postService.findReadableInOrder(postIds, viewerId).stream()
                .map(post -> PostSummary.of(post, viewerId))
                .toList();
        return new NameSearchResult(folders, posts);
    }

    /**
     * 태그 자동완성. 앞의 #은 뗀다.
     */
    public TagSuggestions suggestTags(Long viewerId, String rawQ, int size) {
        String q = query(withoutHash(rawQ), 1, 30);
        return new TagSuggestions(queryRepository.suggestTags(q, viewerId, size).stream()
                .map(row -> new TagSuggestions.Item(row.name(), row.postCount()))
                .toList());
    }

    /**
     * 태그별 글, 최근 수정순. 태그가 없거나 읽을 수 있는 글이 없으면 빈 목록이다.
     */
    public TagPosts postsByTag(Long viewerId, String rawName, boolean mineOnly, String cursor, int size) {
        String name = withoutHash(rawName);
        Optional<Tag> tag = tagService.findByName(name);
        if (tag.isEmpty()) {
            return new TagPosts(new TagPosts.TagRef(name), List.of(), null);
        }
        CursorCodec.Cursor after = (cursor == null) ? null : CursorCodec.decode(cursor);
        List<Long> ids = queryRepository.findPostIdsByTag(tag.get().getId(), viewerId, mineOnly, after, size + 1);
        CursorPage<PostSummary> page = CursorPage.of(postService.findReadableInOrder(ids, viewerId), size,
                post -> PostSummary.of(post, viewerId), this::updatedCursor);
        return new TagPosts(new TagPosts.TagRef(tag.get().getName()), page.items(), page.nextCursor());
    }

    private String updatedCursor(Post post) {
        return CursorCodec.encode(post.getUpdatedAt().toString(), post.getId());
    }

    // 앞뒤 공백을 자르고 길이를 본다. 여러 줄 검색어는 받지 않는다
    private static String query(String raw, int min, int max) {
        String q = raw.strip();
        if (q.length() < min || q.length() > max) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 " + min + "~" + max + "자입니다.");
        }
        if (q.indexOf('\n') >= 0 || q.indexOf('\r') >= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 한 줄이어야 합니다.");
        }
        return q;
    }

    private static String withoutHash(String name) {
        String stripped = name.strip();
        return stripped.startsWith("#") ? stripped.substring(1) : stripped;
    }

}
