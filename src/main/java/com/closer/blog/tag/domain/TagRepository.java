package com.closer.blog.tag.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 없을 때만 만든다. 두 글이 같은 새 태그를 동시에 만들어도 오류가 나지 않는다 (docs/erd.md 4.5절).
     */
    @Modifying
    @Query(value = "INSERT INTO tags (name, created_at) VALUES (:name, :now) ON CONFLICT ((lower(name))) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("name") String name, @Param("now") Instant now);

    @Query("select t from Tag t where lower(t.name) = lower(:name)")
    Optional<Tag> findByNameIgnoreCase(@Param("name") String name);

}