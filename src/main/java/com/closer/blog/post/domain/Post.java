package com.closer.blog.post.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.closer.blog.folder.domain.Folder;
import com.closer.blog.tag.domain.Tag;
import com.closer.blog.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    // 공개 범위 (docs/erd.md 4.4절). 640(링크 공개)은 P1
    public static final int MODE_PUBLIC = 644;

    public static final int MODE_PRIVATE = 600;

    // 휴지통에 이만큼 둔 뒤 예약 작업이 지운다 (docs/erd.md 7절)
    public static final Duration TRASH_RETENTION = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    private String fileName;

    private String title;

    private String content;

    private int mode;

    private int charCount;

    // 낙관적 잠금. 고칠 때마다 Hibernate가 1씩 올린다
    @Version
    private int version;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private Instant deletedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostTag> postTags = new ArrayList<>();

    private Post(Folder folder, String fileName, String title, String content, int mode, Instant now) {
        this.owner = folder.getOwner();
        this.folder = folder;
        this.fileName = fileName;
        this.title = title;
        this.content = content;
        this.charCount = countChars(content);
        this.mode = mode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 새 글. 소유자는 폴더의 소유자다.
     */
    public static Post create(Folder folder, String fileName, String title, String content, int mode, Instant now) {
        return new Post(folder, fileName, title, content, mode, now);
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 홈 기준 경로. python 폴더의 pointer.md는 python/pointer.md
     */
    public String getPath() {
        return folder.isHome() ? fileName : folder.getPath() + "/" + fileName;
    }

    public List<String> getTagNames() {
        return postTags.stream()
                .sorted(Comparator.comparingInt(PostTag::getSortOrder))
                .map(postTag -> postTag.getTag().getName())
                .toList();
    }

    // 아래 change* 메서드는 값이 실제로 바뀌었는지 돌려준다. 바뀐 게 있을 때만 touch()로 수정일을 갱신한다

    public boolean changeTitle(String title) {
        if (this.title.equals(title)) {
            return false;
        }
        this.title = title;
        return true;
    }

    public boolean changeContent(String content) {
        if (this.content.equals(content)) {
            return false;
        }
        this.content = content;
        this.charCount = countChars(content);
        return true;
    }

    public boolean changeMode(int mode) {
        if (this.mode == mode) {
            return false;
        }
        this.mode = mode;
        return true;
    }

    public boolean rename(String fileName) {
        if (this.fileName.equals(fileName)) {
            return false;
        }
        this.fileName = fileName;
        return true;
    }

    public boolean moveTo(Folder folder) {
        if (this.folder.getId().equals(folder.getId())) {
            return false;
        }
        this.folder = folder;
        return true;
    }

    /**
     * 태그를 통째로 바꾼다. 같은 태그 행을 지웠다 다시 넣으면 기본 키(post_id, tag_id)가 부딪치므로
     * 빠진 것만 지우고, 남는 것은 순서만 고치고, 새 것만 넣는다.
     */
    public boolean replaceTags(List<Tag> tags) {
        List<Long> before = postTags.stream()
                .sorted(Comparator.comparingInt(PostTag::getSortOrder))
                .map(postTag -> postTag.getTag().getId())
                .toList();
        List<Long> after = tags.stream().map(Tag::getId).toList();
        if (before.equals(after)) {
            return false;
        }

        postTags.removeIf(postTag -> !after.contains(postTag.getTag().getId()));
        for (int order = 0; order < tags.size(); order++) {
            Tag tag = tags.get(order);
            Optional<PostTag> existing = postTags.stream()
                    .filter(postTag -> Objects.equals(postTag.getTag().getId(), tag.getId()))
                    .findFirst();
            if (existing.isPresent()) {
                existing.get().changeSortOrder(order);
            }
            else {
                postTags.add(new PostTag(this, tag, order));
            }
        }
        return true;
    }

    /**
     * rm. 폴더와 이름은 그대로 두므로 복구하면 원래 자리로 간다.
     */
    public void moveToTrash(Instant now) {
        this.deletedAt = now;
    }

    public void restore() {
        this.deletedAt = null;
    }

    /**
     * 무언가 바뀌었을 때 수정일을 갱신한다. 태그만 바뀌어도 이 글 행이 바뀌므로 version도 오른다.
     */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    // 공백·줄바꿈을 뺀 글자(코드 포인트) 수 (docs/erd.md 4.4절)
    private static int countChars(String content) {
        return (int) content.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
    }
}
