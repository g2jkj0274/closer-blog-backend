package com.closer.blog.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 휴지통에 있는 글은 이름 중복으로 치지 않는다 (docs/erd.md 4.4절)
    boolean existsByFolderIdAndFileNameAndDeletedAtIsNull(Long folderId, String fileName);

}