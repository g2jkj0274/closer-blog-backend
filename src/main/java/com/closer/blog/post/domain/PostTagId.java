package com.closer.blog.post.domain;

import java.io.Serializable;

/**
 * post_tags의 복합 기본 키 (post_id, tag_id). 필드 이름은 PostTag의 @Id 필드 이름과 같아야 한다.
 */
public record PostTagId(Long post, Long tag) implements Serializable {
}