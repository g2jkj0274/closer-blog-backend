package com.closer.blog.search.service;

import java.util.ArrayList;
import java.util.List;

import com.closer.blog.search.dto.ContentSearchResult.MatchedLine;

/**
 * grep의 결과 줄을 만든다 (docs/api-spec.md 9절 GET /search/content). DB는 글을 거르기만 하고,
 * 한 글 안에서 몇 번, 몇째 줄, 어디서 일치했는지는 여기서 센다.
 * <p>
 * 위치는 모두 Java String 인덱스(UTF-16 코드 단위)다. JavaScript 문자열과 같은 단위라 프런트가 그대로 강조에 쓴다.
 * 대소문자는 regionMatches(ignoreCase)로 무시한다. 소문자로 바꾼 뒤 찾으면 글자 수가 바뀌는 문자가 있어 위치가 어긋난다.
 */
public final class LineMatcher {

    // 결과에 싣는 일치한 줄 수
    static final int MAX_LINES = 3;

    // 이보다 긴 줄은 첫 일치 주변만 자른다
    static final int MAX_LINE_LENGTH = 200;

    // 자를 때 첫 일치 앞에 남기는 글자 수
    private static final int CONTEXT_BEFORE = 60;

    private static final String ELLIPSIS = "…";

    private LineMatcher() {
    }

    public static Result match(String content, String q) {
        int matchCount = 0;
        List<MatchedLine> lines = new ArrayList<>();
        String[] rawLines = content.split("\n", -1);
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i].endsWith("\r") ? rawLines[i].substring(0, rawLines[i].length() - 1)
                    : rawLines[i];
            List<int[]> ranges = findAll(line, q);
            matchCount += ranges.size();
            if (!ranges.isEmpty() && lines.size() < MAX_LINES) {
                lines.add(toLine(i + 1, line, ranges));
            }
        }
        return new Result(matchCount, lines);
    }

    // 겹치지 않게 앞에서부터 찾는다
    private static List<int[]> findAll(String line, String q) {
        List<int[]> ranges = new ArrayList<>();
        int from = 0;
        while (from + q.length() <= line.length()) {
            if (line.regionMatches(true, from, q, 0, q.length())) {
                ranges.add(new int[] { from, from + q.length() });
                from += q.length();
            }
            else {
                from++;
            }
        }
        return ranges;
    }

    private static MatchedLine toLine(int lineNo, String line, List<int[]> ranges) {
        if (line.length() <= MAX_LINE_LENGTH) {
            return new MatchedLine(lineNo, line, ranges);
        }
        // 첫 일치가 보이도록 MAX_LINE_LENGTH만큼 자른다
        int start = Math.max(0, Math.min(ranges.getFirst()[0] - CONTEXT_BEFORE, line.length() - MAX_LINE_LENGTH));
        int end = start + MAX_LINE_LENGTH;
        // 이모지 같은 서로게이트 쌍을 반으로 가르지 않는다
        if (start > 0 && Character.isLowSurrogate(line.charAt(start))) {
            start--;
        }
        if (end < line.length() && Character.isLowSurrogate(line.charAt(end))) {
            end++;
        }

        String prefix = (start > 0) ? ELLIPSIS : "";
        String suffix = (end < line.length()) ? ELLIPSIS : "";
        int shift = prefix.length() - start;
        List<int[]> shifted = new ArrayList<>();
        for (int[] range : ranges) {
            if (range[0] >= start && range[1] <= end) {
                shifted.add(new int[] { range[0] + shift, range[1] + shift });
            }
        }
        return new MatchedLine(lineNo, prefix + line.substring(start, end) + suffix, shifted);
    }

    /**
     * @param matchCount 글 전체의 일치 횟수
     * @param lines 일치한 줄 중 앞에서부터 최대 3줄
     */
    public record Result(int matchCount, List<MatchedLine> lines) {
    }

}
