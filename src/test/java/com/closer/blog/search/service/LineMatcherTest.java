package com.closer.blog.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.closer.blog.search.dto.ContentSearchResult.MatchedLine;
import org.junit.jupiter.api.Test;

/**
 * 스프링 없이 도는 단위 테스트. grep 결과 줄의 규칙(docs/api-spec.md 9절)을 확인한다.
 */
class LineMatcherTest {

    @Test
    void countsAllMatchesIgnoringCaseAndKeepsFirstThreeLines() {
        LineMatcher.Result result = LineMatcher.match(
                "Pointer\nno\npointer POINTER\nx pointer\nlast pointer", "pointer");

        assertThat(result.matchCount()).isEqualTo(5);
        assertThat(result.lines()).extracting(MatchedLine::lineNo).containsExactly(1, 3, 4);
        assertThat(result.lines().get(1).ranges()).containsExactly(new int[] { 0, 7 }, new int[] { 8, 15 });
    }

    @Test
    void noMatchAndWindowsLineEndings() {
        LineMatcher.Result result = LineMatcher.match("a\r\nb", "zz");

        assertThat(result.matchCount()).isZero();
        assertThat(result.lines()).isEmpty();
        // \r은 줄 끝에서 떼므로 text에 남지 않는다
        assertThat(LineMatcher.match("ab\r\ncd", "ab").lines().getFirst().text()).isEqualTo("ab");
    }

    @Test
    void longLineIsCutAroundFirstMatch() {
        String line = "a".repeat(300) + "포인터" + "b".repeat(300);

        MatchedLine matched = LineMatcher.match(line, "포인터").lines().getFirst();

        assertThat(matched.text()).startsWith("…").endsWith("…");
        assertThat(matched.text()).hasSize(LineMatcher.MAX_LINE_LENGTH + 2);
        int[] range = matched.ranges().getFirst();
        assertThat(matched.text().substring(range[0], range[1])).isEqualTo("포인터");
    }

    @Test
    void matchAtEndOfLongLineHasNoTrailingEllipsis() {
        String line = "a".repeat(300) + "끝";

        MatchedLine matched = LineMatcher.match(line, "끝").lines().getFirst();

        assertThat(matched.text()).startsWith("…").endsWith("끝");
        int[] range = matched.ranges().getFirst();
        assertThat(matched.text().substring(range[0], range[1])).isEqualTo("끝");
    }

    @Test
    void cutDoesNotSplitSurrogatePairs() {
        // 이모지 하나가 UTF-16 두 칸이다
        String line = "😀".repeat(150) + "x";

        MatchedLine matched = LineMatcher.match(line, "x").lines().getFirst();

        assertThat(Character.isLowSurrogate(matched.text().charAt(1))).isFalse();
        int[] range = matched.ranges().getFirst();
        assertThat(matched.text().substring(range[0], range[1])).isEqualTo("x");
    }

}
