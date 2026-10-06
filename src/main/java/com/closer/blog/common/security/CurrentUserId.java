package com.closer.blog.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 로그인한 사용자의 id. 액세스 토큰(JWT)의 sub 클레임이다.
 * <pre>
 * ResponseEntity&lt;...&gt; create(@CurrentUserId Long userId, ...)
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface CurrentUserId {
}