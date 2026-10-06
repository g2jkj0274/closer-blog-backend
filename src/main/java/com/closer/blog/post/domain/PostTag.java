package com.closer.blog.post.domain;

import com.closer.blog.tag.domain.Tag;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 글에 붙은 태그 하나. 글이 관리하므로 post 패키지에 있다 (docs/erd.md 1절).
 */
@Entity
@Table(name = "post_tags")
@IdClass(PostTagId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTag {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    // 글쓴이가 단 순서
    private int sortOrder;

    PostTag(Post post, Tag tag, int sortOrder) {
        this.post = post;
        this.tag = tag;
        this.sortOrder = sortOrder;
    }

    void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

}
