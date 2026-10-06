package com.closer.blog.folder.domain;

import java.time.Instant;

import com.closer.blog.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "folders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", updatable = false)
    private User owner;

    // NULL이면 그 사용자의 홈(~)이다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private Folder parent;

    @Column(updatable = false)
    private String name;

    @Column(updatable = false)
    private String path;

    @Column(updatable = false)
    private Instant createdAt;

    private Folder(User owner, Folder parent, String name, String path, Instant createdAt) {
        this.owner = owner;
        this.parent = parent;
        this.name = name;
        this.path = path;
        this.createdAt = createdAt;
    }

    /**
     * 홈 폴더. 이름과 경로가 빈 문자열이다 (docs/erd.md 4.3절).
     */
    public static Folder home(User owner, Instant now) {
        return new Folder(owner, null, "", "", now);
    }

    public boolean isHome() {
        return parent == null;
    }
}
