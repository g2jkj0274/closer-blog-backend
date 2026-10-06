package com.closer.blog.post.service;

import java.time.Clock;
import java.util.List;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.common.web.CursorCodec;
import com.closer.blog.common.web.CursorPage;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.service.FolderService;
import com.closer.blog.post.domain.Post;
import com.closer.blog.post.domain.PostRepository;
import com.closer.blog.post.dto.CreatePostRequest;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.dto.PostSummary;
import com.closer.blog.post.dto.TrashItem;
import com.closer.blog.post.dto.UpdatePostRequest;
import com.closer.blog.tag.domain.Tag;
import com.closer.blog.tag.service.TagService;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글 만들기·읽기·고치기와 휴지통. PostDetail은 지연 로딩 연관(폴더, 소유자, 태그)을 읽으므로 트랜잭션 안에서 만들어 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

    private final FolderService folderService;

    private final TagService tagService;

    private final UserService userService;

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

    /**
     * 그 사람의 글을 폴더와 상관없이 최근 수정순으로. 보는 사람이 읽을 수 있는 것만 나온다.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostSummary> listByOwner(Long viewerId, String username, String cursor, int size) {
        User owner = userService.getByUsername(username);
        Limit limit = Limit.of(size + 1);
        List<Post> rows;
        if (cursor == null) {
            rows = postRepository.findReadableByOwner(owner.getId(), viewerId, limit);
        }
        else {
            CursorCodec.Cursor after = CursorCodec.decode(cursor);
            rows = postRepository.findReadableByOwnerAfter(owner.getId(), viewerId, after.sortKeyAsInstant(),
                    after.id(), limit);
        }
        return CursorPage.of(rows, size, post -> PostSummary.of(post, viewerId),
                post -> CursorCodec.encode(post.getUpdatedAt().toString(), post.getId()));
    }

    /**
     * rm. 휴지통으로 보낸다.
     */
    @Transactional
    public void moveToTrash(Long userId, Long postId) {
        Post post = findPost(postId);
        accessPolicy.checkWritable(post, userId);
        post.moveToTrash(clock.instant());
    }

    /**
     * 내 휴지통. 최근에 지운 것부터. 다음 페이지가 있는지 보려고 하나 더 읽는다.
     */
    @Transactional(readOnly = true)
    public CursorPage<TrashItem> listTrash(Long userId, String cursor, int size) {
        Limit limit = Limit.of(size + 1);
        List<Post> rows;
        if (cursor == null) {
            rows = postRepository.findTrash(userId, limit);
        }
        else {
            CursorCodec.Cursor after = CursorCodec.decode(cursor);
            rows = postRepository.findTrashAfter(userId, after.sortKeyAsInstant(), after.id(), limit);
        }
        return CursorPage.of(rows, size, TrashItem::of,
                post -> CursorCodec.encode(post.getDeletedAt().toString(), post.getId()));
    }

    /**
     * 원래 폴더로 되돌린다. 그 자리에 같은 이름의 글이 생겨 있으면 409이고, 새 이름을 받아 다시 복구한다.
     */
    @Transactional
    public PostDetail restore(Long userId, Long postId, String newFileName) {
        Post post = findPost(postId);
        accessPolicy.checkInTrashOf(post, userId);
        String fileName = (newFileName == null) ? post.getFileName() : newFileName;
        // update()와 같은 까닭으로 검사를 먼저 하고 글을 고친다
        checkNameAvailable(post.getFolder(), fileName);

        post.restore();
        post.rename(fileName);
        postRepository.flush();
        return PostDetail.of(post, userId);
    }

    /**
     * 휴지통에서 rm -f. 되돌릴 수 없다.
     */
    @Transactional
    public void purge(Long userId, Long postId) {
        Post post = findPost(postId);
        accessPolicy.checkInTrashOf(post, userId);
        postRepository.delete(post);
    }

    /**
     * 휴지통에 30일 넘게 있던 글을 모두 지운다. 지운 개수를 돌려준다.
     */
    @Transactional
    public int purgeExpired() {
        return postRepository.deleteTrashedBefore(clock.instant().minus(Post.TRASH_RETENTION));
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
