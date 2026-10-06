package com.closer.blog.folder.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByOwnerIdAndParentIsNull(Long ownerId);

    boolean existsByParentIdAndName(Long parentId, String name);

    boolean existsByParentId(Long parentId);

    // 홈은 path가 ""다 (docs/erd.md 4.3절)
    Optional<Folder> findByOwnerIdAndPath(Long ownerId, String path);

    List<Folder> findByParentId(Long parentId);

    List<Folder> findByOwnerId(Long ownerId);

}