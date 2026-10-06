package com.closer.blog.folder.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByOwnerIdAndParentIsNull(Long ownerId);

    boolean existsByParentIdAndName(Long parentId, String name);

    boolean existsByParentId(Long parentId);

}