package com.closer.blog.post.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 휴지통에 있는 글은 이름 중복으로 치지 않는다 (docs/erd.md 4.4절)
    boolean existsByFolderIdAndFileNameAndDeletedAtIsNull(Long folderId, String fileName);

    // 휴지통 첫 페이지. 최근에 지운 것부터. 경로를 만들려고 폴더를 함께 읽는다
    @Query("""
            select p from Post p join fetch p.folder
            where p.owner.id = :ownerId and p.deletedAt is not null
            order by p.deletedAt desc, p.id desc""")
    List<Post> findTrash(@Param("ownerId") Long ownerId, Limit limit);

    // 휴지통 다음 페이지. (deletedAt, id)가 커서보다 뒤인 것
    @Query("""
            select p from Post p join fetch p.folder
            where p.owner.id = :ownerId and p.deletedAt is not null
              and (p.deletedAt < :deletedAt or (p.deletedAt = :deletedAt and p.id < :id))
            order by p.deletedAt desc, p.id desc""")
    List<Post> findTrashAfter(@Param("ownerId") Long ownerId, @Param("deletedAt") Instant deletedAt,
                              @Param("id") Long id, Limit limit);

    // post_tags 행은 DB의 ON DELETE CASCADE가 지운다
    @Modifying
    @Query("delete from Post p where p.deletedAt < :before")
    int deleteTrashedBefore(@Param("before") Instant before);

}