package com.closer.blog.common.web;

/**
 * 화면에 보이는 절대 경로. 보는 사람 기준이라 내 것이면 ~, 남의 것이면 /users/{아이디}로 시작한다
 * (docs/api-spec.md 1.6절).
 */
public final class DisplayPath {

    private DisplayPath() {
    }

    /**
     * @param viewerIsOwner 보는 사람이 소유자인가
     * @param ownerUsername 소유자 아이디
     * @param path 홈 기준 상대 경로. 홈은 ""
     */
    public static String of(boolean viewerIsOwner, String ownerUsername, String path) {
        String root = viewerIsOwner ? "~" : "/users/" + ownerUsername;
        return path.isEmpty() ? root : root + "/" + path;
    }

}