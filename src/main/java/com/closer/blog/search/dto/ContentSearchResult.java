package com.closer.blog.search.dto;

import java.util.List;

import com.closer.blog.post.dto.PostSummary;

/**
 * grep의 결과 (docs/api-spec.md 9절 GET /search/content).
 *
 * @param fileCount 일치한 글 수. 첫 페이지에만 있고 다음 페이지부터는 null
 */
public record ContentSearchResult(Long fileCount, List<Hit> items, String nextCursor) {

    /**
     * @param matchCount 그 글 안의 일치 횟수
     * @param lines 일치한 줄 중 앞에서부터 최대 3줄
     */
    public record Hit(PostSummary post, int matchCount, List<MatchedLine> lines) {
    }

    /**
     * @param lineNo 1부터 센다
     * @param text 200자보다 긴 줄은 첫 일치 주변만 남기고 잘린 쪽에 …를 붙인다
     * @param ranges text 안에서 일치한 구간 [시작, 끝). UTF-16 코드 단위
     */
    public record MatchedLine(int lineNo, String text, List<int[]> ranges) {
    }

}
