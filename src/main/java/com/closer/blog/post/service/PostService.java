package com.closer.blog.post.service;

import java.time.Clock;
import java.util.List;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.service.FolderService;
import com.closer.blog.post.domain.Post;
import com.closer.blog.post.domain.PostRepository;
import com.closer.blog.post.dto.CreatePostRequest;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.UpdatePostRequest;
import com.closer.blog.tag.domain.Tag;
import com.closer.blog.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 만들기·읽기·고치기. PostDetail은 지연 로딩 연관(폴더, 소유자, 태그)을 읽으므로 트랜잭션 안에서 만들어 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    private final FolderService folderService;

    private final TagService tagService;

    private final PostAccessPolicy accessPolicy;

    private final Clock clock;

    /**
     * 첫 :w. 내 폴더에만 쓴다.
     */
    @Transactional
    public PostDetail create(Long userId, CreatePostRequest request) {
        Folder folder = folderService.findOwnedFolder(userId, request.folderId(), "다른 사람의 폴더에는 글을 쓸 수 없습니다.");
        checkNameAvailable(folder, request.fileName());
        int mode = (request.mode() == null) ? Post.MODE_PUBLIC : checkMode(request.mode());

        Post post = Post.create(folder, request.fileName(), orEmpty(request.title()), orEmpty(request.content()), mode,
                clock.instant());
        post.replaceTags(tagService.resolve(request.tags() == null ? List.of() : request.tags()));
        // 동시에 같은 이름으로 만들면 uq_posts_folder_file_name에 걸리고 GlobalExceptionHandler가 409로 바꾼다
        postRepository.saveAndFlush(post);
        return PostDetail.of(post, userId);
    }

    @Transactional(readOnly = true)
    public PostDetail get(Long viewerId, Long postId) {
        Post post = findPost(postId);
        accessPolicy.checkReadable(post, viewerId);
        return PostDetail.of(post, viewerId);
    }

    /**
     * :w, mv, chmod. 보낸 필드만 바꾼다.
     */
    @Transactional
    public PostDetail update(Long userId, Long postId, UpdatePostRequest request) {
        Post post = findPost(postId);
        accessPolicy.checkWritable(post, userId);
        if (request.version() != null && request.version() != post.getVersion()) {
            throw new ApiException(ErrorCode.POST_VERSION_CONFLICT, ErrorCode.POST_VERSION_CONFLICT.getMessage());
        }

        // 검사를 모두 끝낸 뒤에 글을 고친다. 고친 뒤에 조회하면 Hibernate가 바뀐 글을 먼저 DB에 써서
        // 이름 중복 검사가 자기 자신을 찾는다
        Folder folder = (request.folderId() == null) ? post.getFolder()
                : folderService.findOwnedFolder(userId, request.folderId(), "다른 사람의 폴더로는 옮길 수 없습니다.");
        String fileName = (request.fileName() == null) ? post.getFileName() : request.fileName();
        boolean moving = !folder.getId().equals(post.getFolder().getId()) || !fileName.equals(post.getFileName());
        if (moving) {
            checkNameAvailable(folder, fileName);
        }
        if (request.mode() != null) {
            checkMode(request.mode());
        }
        List<Tag> tags = (request.tags() == null) ? null : tagService.resolve(request.tags());

        boolean changed = false;
        if (request.title() != null) {
            changed |= post.changeTitle(request.title());
        }
        if (request.content() != null) {
            changed |= post.changeContent(request.content());
        }
        if (tags != null) {
            changed |= post.replaceTags(tags);
        }
        if (request.mode() != null) {
            changed |= post.changeMode(request.mode());
        }
        changed |= post.moveTo(folder);
        changed |= post.rename(fileName);

        if (changed) {
            post.touch(clock.instant());
            // 지금 DB에 써야 응답의 version이 올라간 값이 된다. 그 사이 누가 먼저 고쳤으면 여기서 409다
            postRepository.flush();
        }
        return PostDetail.of(post, userId);
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId).orElseThrow(accessPolicy::notFound);
    }

    private void checkNameAvailable(Folder folder, String fileName) {
        if (postRepository.existsByFolderIdAndFileNameAndDeletedAtIsNull(folder.getId(), fileName)) {
            throw new ApiException(ErrorCode.POST_NAME_TAKEN, "같은 폴더에 " + fileName + "이(가) 이미 있습니다.");
        }
    }

    // 640(링크 공개)은 P1이라 아직 받지 않는다
    private static int checkMode(int mode) {
        if (mode != Post.MODE_PUBLIC && mode != Post.MODE_PRIVATE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "공개 범위는 644 또는 600입니다.");
        }
        return mode;
    }

    private static String orEmpty(String value) {
        return (value == null) ? "" : value;
    }
}
