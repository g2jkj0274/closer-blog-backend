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

    /**
     * 하위 폴더. 소유자는 부모와 같고, 경로는 부모 경로 뒤에 이름을 붙인다.
     */
    public static Folder child(Folder parent, String name, Instant now) {
        String path = parent.isHome() ? name : parent.getPath() + "/" + name;
        return new Folder(parent.getOwner(), parent, name, path, now);
    }

    public boolean isHome() {
        return parent == null;
    }

    public boolean isOwnedBy(Long userId) {
        // 지연 로딩 프록시라도 getId()는 DB를 읽지 않는다
        return owner.getId().equals(userId);
    }

    /**
     * 홈 아래 몇 단계인가. 홈은 0, python은 1, python/basic은 2.
     */
    public int depth() {
        return path.isEmpty() ? 0 : path.split("/").length;
    }
}
