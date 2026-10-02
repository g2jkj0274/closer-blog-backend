package com.closer.blog.common.error;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice           // 모든 컨트롤러에서 나온 예외를 이 클래스가 받아 JSON으로 응답
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // DB 제약 이름 -> 오류 코드 (docs/erd.md 6절 "제약 위반과 API 오류")
    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_CODES = Map.of(
            "uq_users_username", ErrorCode.USERNAME_TAKEN,
            "uq_users_email", ErrorCode.EMAIL_TAKEN,
            "uq_folders_parent_name", ErrorCode.FOLDER_NAME_TAKEN,
            "uq_folders_owner_path", ErrorCode.FOLDER_NAME_TAKEN,
            "uq_posts_folder_file_name", ErrorCode.POST_NAME_TAKEN);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    // 보안 필터에서 SecurityErrorHandler가 넘겨준다
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleInvalidParameter(HandlerMethodValidationException ex) {
        List<ErrorResponse.FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), errors);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class })
    ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        return respond(ErrorCode.VALIDATION_ERROR, "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    // @Version 충돌. 낙관적 잠금을 쓰는 엔티티는 Post뿐이다.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return respond(ErrorCode.POST_VERSION_CONFLICT, ErrorCode.POST_VERSION_CONFLICT.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String constraintName = findConstraintName(ex);
        ErrorCode code = (constraintName != null) ? CONSTRAINT_ERROR_CODES.get(constraintName) : null;
        if (code != null) {
            return respond(code, code.getMessage());
        }
        if (constraintName != null && constraintName.startsWith("ck_")) {
            // API 검증이 먼저 막아야 하는 값이다. 여기까지 오면 검증 누락이다.
            log.warn("DB CHECK 제약 {}에 걸린 요청", constraintName, ex);
            return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage());
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("처리하지 못한 예외", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private static String findConstraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    // 모든 응답을 같은 모양으로 만드는 공통 메서드
    private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
        return respond(code, message, List.of());
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message,
                                                         List<ErrorResponse.FieldViolation> errors) {
        return ResponseEntity.status(code.getStatus()).body(new ErrorResponse(code.name(), message, errors));
    }
}
