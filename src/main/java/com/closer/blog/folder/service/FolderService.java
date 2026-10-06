package com.closer.blog.folder.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FolderService {

    // 홈 아래 10단계까지 (docs/erd.md 4.3절)
    private static final int MAX_DEPTH = 10;

    private final FolderRepository folderRepository;

    private final Clock clock;

    @Transactional
    public Folder createHome(User owner) {
        return folderRepository.saveAndFlush(Folder.home(owner, clock.instant()));
    }

    /**
     * mkdir. 내 폴더 아래에만 만든다.
     */
    @Transactional
    public Folder create(Long userId, Long parentId, String name) {
        Folder parent = findOwnedFolder(userId, parentId, "다른 사람의 폴더에는 만들 수 없습니다.");
        if (parent.depth() >= MAX_DEPTH) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "폴더는 홈 아래 " + MAX_DEPTH + "단계까지 만들 수 있습니다.");
        }
        if (folderRepository.existsByParentIdAndName(parentId, name)) {
            throw new ApiException(ErrorCode.FOLDER_NAME_TAKEN, "같은 폴더에 " + name + "이(가) 이미 있습니다.");
        }
        // 동시에 같은 이름을 만들면 uq_folders_parent_name에 걸리고 GlobalExceptionHandler가 409로 바꾼다
        return folderRepository.saveAndFlush(Folder.child(parent, name, clock.instant()));
    }

    /**
     * rmdir. 비어 있을 때만 지운다.
     */
    @Transactional
    public void delete(Long userId, Long folderId) {
        Folder folder = findOwnedFolder(userId, folderId, "다른 사람의 폴더는 지울 수 없습니다.");
        if (folder.isHome()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "홈 폴더는 지울 수 없습니다.");
        }
        if (folderRepository.existsByParentId(folderId)) {
            throw new ApiException(ErrorCode.FOLDER_NOT_EMPTY, "하위 폴더가 남아 있어 지울 수 없습니다.");
        }
        try {
            folderRepository.delete(folder);
            folderRepository.flush();
        }
        catch (DataIntegrityViolationException ex) {
            // 글(휴지통의 글 포함)이 남아 있으면 posts의 외래 키가 삭제를 막는다.
            // folder 패키지는 post를 모르므로 글 검사를 DB 제약에 맡긴다 (docs/api-spec.md 2절)
            throw new ApiException(ErrorCode.FOLDER_NOT_EMPTY,
                    "폴더에 글이 남아 있어 지울 수 없습니다. 휴지통에 있는 글도 비우거나 복구해야 합니다.");
        }
    }

    /**
     * 폴더는 로그인한 누구나 볼 수 있다. 없으면 404다.
     */
    @Transactional(readOnly = true)
    public Folder get(Long folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "없는 폴더입니다."));
    }

    /**
     * 홈 기준 경로로 찾는다. 홈은 "".
     */
    @Transactional(readOnly = true)
    public Optional<Folder> findByPath(Long ownerId, String path) {
        return folderRepository.findByOwnerIdAndPath(ownerId, path);
    }

    @Transactional(readOnly = true)
    public List<Folder> findChildren(Long parentId) {
        return folderRepository.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public List<Folder> findAllOf(Long ownerId) {
        return folderRepository.findByOwnerId(ownerId);
    }

    /**
     * 내 폴더를 찾는다. 없으면 404, 남의 것이면 403. 폴더는 누구나 볼 수 있으므로 존재를 숨기지 않는다
     * (docs/api-spec.md 1.3절). 글을 쓰거나 옮길 때 post 패키지도 쓴다.
     */
    public Folder findOwnedFolder(Long userId, Long folderId, String forbiddenMessage) {
        Folder folder = get(folderId);
        if (!folder.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, forbiddenMessage);
        }
        return folder;
    }

}
