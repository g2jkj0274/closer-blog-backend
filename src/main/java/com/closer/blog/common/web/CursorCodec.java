package com.closer.blog.common.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;

/**
 * 목록 커서. 마지막 항목의 정렬 키와 id를 base64url로 감싼다 (docs/api-spec.md 1.5절).
 * 프런트는 해석하지 않고 그대로 돌려보낸다.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {
    }

    public static String encode(String sortKey, long id) {
        String raw = sortKey + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 프런트가 고쳤거나 잘린 커서는 400이다.
     */
    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int at = raw.lastIndexOf(SEPARATOR);
            if (at < 0) {
                throw invalid();
            }
            return new Cursor(raw.substring(0, at), Long.parseLong(raw.substring(at + 1)));
        }
        catch (IllegalArgumentException ex) {
            // Base64 디코딩 실패와 NumberFormatException이 모두 여기로 온다
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "cursor가 올바르지 않습니다. 처음부터 다시 불러오세요.");
    }

    public record Cursor(String sortKey, long id) {

        public long sortKeyAsLong() {
            try {
                return Long.parseLong(sortKey);
            }
            catch (NumberFormatException ex) {
                throw invalid();
            }
        }

        public Instant sortKeyAsInstant() {
            try {
                return Instant.parse(sortKey);
            }
            catch (DateTimeParseException ex) {
                throw invalid();
            }
        }

    }

}
