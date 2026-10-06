package com.closer.blog.common.web;

import java.util.List;
import java.util.function.Function;

/**
 * 커서로 나눈 목록 한 페이지 (docs/api-spec.md 1.5절). 마지막 페이지면 nextCursor가 null이다.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    /**
     * size + 1개를 읽어 온 rows로 한 페이지를 만든다. 하나가 남으면 다음 페이지가 있다.
     *
     * @param mapper 행을 응답 항목으로 바꾼다
     * @param cursorOf 이 페이지 마지막 행의 커서를 만든다
     */
    public static <R, T> CursorPage<T> of(List<R> rows, int size, Function<R, T> mapper,
                                          Function<R, String> cursorOf) {
        boolean hasNext = rows.size() > size;
        List<R> page = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? cursorOf.apply(page.getLast()) : null;
        return new CursorPage<>(page.stream().map(mapper).toList(), nextCursor);
    }

}
