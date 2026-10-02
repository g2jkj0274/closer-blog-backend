package com.closer.blog.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_PATH(HttpStatus.BAD_REQUEST, "경로 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "없는 경로입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    FOLDER_NAME_TAKEN(HttpStatus.CONFLICT, "같은 이름의 폴더가 이미 있습니다."),
    POST_NAME_TAKEN(HttpStatus.CONFLICT, "같은 이름의 글이 이미 있습니다."),
    FOLDER_NOT_EMPTY(HttpStatus.CONFLICT, "비어 있지 않은 폴더는 지울 수 없습니다."),
    POST_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 고친 글입니다. 다시 불러온 뒤 저장하세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다. JSON으로 보내세요."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "로그인 시도가 많아 잠시 잠겼습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;

    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
