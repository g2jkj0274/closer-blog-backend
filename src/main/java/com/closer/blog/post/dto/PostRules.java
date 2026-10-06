package com.closer.blog.post.dto;

/**
 * 글 요청의 검증 규칙. 만들기와 고치기가 함께 쓴다 (docs/erd.md 3절 "이름 규칙").
 */
final class PostRules {

    // 4~100자, 소문자 .md로 끝남. /, 공백, 제어 문자 불가. .으로 시작 불가
    static final String FILE_NAME = "^(?!\\.)[^/\\s\\p{Cntrl}]{1,97}\\.md$";

    static final String FILE_NAME_MESSAGE = "파일 이름은 .md로 끝나는 100자 이하여야 하고 /, 공백을 쓸 수 없습니다. .으로 시작할 수 없습니다.";

    // 앞의 #은 허용하고 떼어 저장한다. 1~30자, #, /, 공백 불가
    static final String TAG = "^#?[^#/\\s\\p{Cntrl}]{1,30}$";

    static final String TAG_MESSAGE = "태그는 1~30자이고 #, /, 공백을 쓸 수 없습니다.";

    static final int MAX_TAGS = 10;

    static final int MAX_CONTENT_BYTES = 1_048_576;

    private PostRules() {
    }

}