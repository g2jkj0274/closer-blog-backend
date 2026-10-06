package com.closer.blog.common.web;

import java.util.List;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;

/**
 * GET /fs가 받는 절대 경로의 문법 (docs/api-spec.md 7절 GET /fs).
 * <pre>
 * ~, ~/python, ~/python/pointer.md, /users/kim, /users/kim/spring/security.md
 * </pre>
 * 문법만 본다. 사용자, 폴더, 글이 있는지는 부르는 쪽이 찾는다.
 */
public final class PathSyntax {

    private static final String USERS_PREFIX = "/users/";

    private static final String POST_SUFFIX = ".md";

    private PathSyntax() {
    }

    public static ParsedPath parse(String raw) {
        String username;
        String rest;
        if (raw.equals("~") || raw.startsWith("~/")) {
            username = null;
            rest = raw.substring(1);
        }
        else if (raw.startsWith(USERS_PREFIX)) {
            String afterUsers = raw.substring(USERS_PREFIX.length());
            int slash = afterUsers.indexOf('/');
            username = (slash < 0) ? afterUsers : afterUsers.substring(0, slash);
            rest = (slash < 0) ? "" : afterUsers.substring(slash);
            if (username.isEmpty()) {
                throw invalid();
            }
        }
        else {
            // /users 목록, 상대 경로 등. 상대 경로는 프런트가 절대 경로로 바꿔 보낸다
            throw invalid();
        }

        // rest는 "" 또는 "/python/pointer.md" 꼴이다. 끝의 / 하나는 무시한다
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        List<String> segments = rest.isEmpty() ? List.of() : List.of(rest.substring(1).split("/", -1));
        for (String segment : segments) {
            // 빈 구간(//), ., .., .trash 같은 시스템 폴더를 모두 막는다
            if (segment.isEmpty() || segment.startsWith(".")) {
                throw invalid();
            }
        }

        if (!segments.isEmpty() && segments.getLast().endsWith(POST_SUFFIX)) {
            List<String> folders = segments.subList(0, segments.size() - 1);
            return new ParsedPath(username, String.join("/", folders), segments.getLast());
        }
        return new ParsedPath(username, String.join("/", segments), null);
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_PATH, ErrorCode.INVALID_PATH.getMessage()
                + " ~ 또는 /users/{아이디}로 시작하는 경로를 보내세요.");
    }

    /**
     * @param username 경로의 소유자. ~로 시작하면 null(보는 사람 자신)
     * @param folderPath 홈 기준 폴더 경로. 홈은 ""
     * @param fileName 글이면 파일 이름, 폴더면 null
     */
    public record ParsedPath(String username, String folderPath, String fileName) {

        public boolean isMine() {
            return username == null;
        }

        public boolean isPost() {
            return fileName != null;
        }

    }

}
