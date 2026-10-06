package com.closer.blog.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 문자열을 UTF-8로 바꿨을 때의 바이트 수 상한. null은 통과시킨다 (@NotNull과 함께 쓴다).
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "UTF-8로 {value}바이트 이하여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}