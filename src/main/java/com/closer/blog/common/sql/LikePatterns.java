package com.closer.blog.common.sql;

/**
 * 사용자가 입력한 검색어로 LIKE·ILIKE 패턴을 만든다. 검색어의 \, %, _는 앞에 \를 붙여 글자로 만든다
 * (docs/erd.md 5.3절). 쿼리에는 ESCAPE '\'를 함께 쓴다.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    // 부분 일치: %검색어%
    public static String contains(String q) {
        return "%" + escape(q) + "%";
    }

    // 앞부분 일치: 검색어%
    public static String startsWith(String q) {
        return escape(q) + "%";
    }

    private static String escape(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
