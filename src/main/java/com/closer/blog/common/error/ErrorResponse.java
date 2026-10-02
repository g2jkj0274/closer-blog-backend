package com.closer.blog.common.error;

import java.util.List;

public record ErrorResponse(
        String code, String message, List<FieldViolation> errors
) {
    public record FieldViolation(String field, String reason) {
    }
}
