package com.closer.blog.fs.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.closer.blog.common.error.ApiException;
import com.closer.blog.common.error.ErrorCode;
import com.closer.blog.common.web.DisplayPath;
import com.closer.blog.common.web.PathSyntax;
import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.dto.FolderSummary;
import com.closer.blog.folder.service.FolderService;
import com.closer.blog.fs.dto.FolderListing;
import com.closer.blog.fs.dto.FolderTree;
import com.closer.blog.fs.dto.FsResponse;
import com.closer.blog.fs.repository.FolderStatsRepository;
import com.closer.blog.fs.repository.FolderStatsRepository.FolderStats;
import com.closer.blog.post.dto.PostDetail;
import com.closer.blog.post.service.PostService;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경로와 폴더를 읽기만 한다. 폴더는 누구나 볼 수 있고, 글은 읽을 수 있는 것만 보이고 센다.
 * 지연 로딩 연관(소유자, 부모 폴더)을 읽으므로 모든 메서드가 읽기 전용 트랜잭션 안에서 돈다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FsService {

    // GET /folders/{id}의 글 목록 기본 크기 (docs/api-spec.md 7절)
    public static final int DEFAULT_POST_PAGE_SIZE = 50;

    private static final Comparator<Folder> BY_NAME = Comparator.comparing(Folder::getName);

    private final UserService userService;

    private final FolderService folderService;

    private final PostService postService;

    private final FolderStatsRepository statsRepository;

    /**
     * GET /fs. 경로를 폴더나 글로 바꾼다. 없는 사용자, 폴더, 글과 읽을 수 없는 글은 모두 404다.
     */
    public FsResponse resolve(Long viewerId, String rawPath) {
        PathSyntax.ParsedPath path = PathSyntax.parse(rawPath);
        Long ownerId = path.isMine() ? viewerId : userService.getByUsername(path.username()).getId();
        Folder folder = folderService.findByPath(ownerId, path.folderPath())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage()));
        if (path.isPost()) {
            PostDetail post = postService.getInFolder(viewerId, folder.getId(), path.fileName());
            return FsResponse.PostResult.of(post);
        }
        return FsResponse.FolderResult.of(listing(viewerId, folder, false, null, DEFAULT_POST_PAGE_SIZE));
    }

    /**
     * GET /folders/{id}. byUpdated면 최근 수정순, 아니면 이름순.
     */
    public FolderListing listFolder(Long viewerId, Long folderId, boolean byUpdated, String cursor, int size) {
        return listing(viewerId, folderService.get(folderId), byUpdated, cursor, size);
    }

    /**
     * GET /users/{username}/tree. 그 사람의 폴더 전체를 한 번에 준다.
     */
    public FolderTree tree(Long viewerId, String username) {
        User owner = userService.getByUsername(username);
        List<Folder> folders = folderService.findAllOf(owner.getId());
        Map<Long, FolderStats> stats = statsRepository.statsOf(folders.stream().map(Folder::getId).toList(), viewerId);
        // 부모 id → 자식 폴더들. getParent()는 지연 로딩 프록시지만 getId()는 DB를 읽지 않는다
        Map<Long, List<Folder>> childrenByParent = folders.stream()
                .filter(folder -> !folder.isHome())
                .collect(Collectors.groupingBy(folder -> folder.getParent().getId()));
        Folder home = folders.stream().filter(Folder::isHome).findFirst().orElseThrow();
        boolean mine = owner.getId().equals(viewerId);
        return new FolderTree(node(home, mine, owner.getUsername(), stats, childrenByParent));
    }

    private FolderTree.Node node(Folder folder, boolean mine, String ownerUsername, Map<Long, FolderStats> stats,
                                 Map<Long, List<Folder>> childrenByParent) {
        List<FolderTree.Node> children = childrenByParent.getOrDefault(folder.getId(), List.of()).stream()
                .sorted(BY_NAME)
                .map(child -> node(child, mine, ownerUsername, stats, childrenByParent))
                .toList();
        FolderStats folderStats = stats.get(folder.getId());
        return new FolderTree.Node(folder.getId(), folder.getName(), folder.getPath(),
                DisplayPath.of(mine, ownerUsername, folder.getPath()), folderStats.postCount(),
                folderStats.folderCount(), folder.getCreatedAt(), children);
    }

    private FolderListing listing(Long viewerId, Folder folder, boolean byUpdated, String cursor, int size) {
        User owner = folder.getOwner();
        boolean mine = folder.isOwnedBy(viewerId);
        List<Folder> children = folderService.findChildren(folder.getId());
        Map<Long, FolderStats> stats = statsRepository.statsOf(
                Stream.concat(Stream.of(folder), children.stream()).map(Folder::getId).toList(), viewerId);

        FolderStats own = stats.get(folder.getId());
        FolderListing.FolderInfo info = new FolderListing.FolderInfo(folder.getId(), folder.getName(),
                folder.getPath(), DisplayPath.of(mine, owner.getUsername(), folder.getPath()), own.postCount(),
                own.folderCount(), folder.getCreatedAt(),
                new PostDetail.UserRef(owner.getUsername(), owner.getDisplayName()), mine);
        FolderListing.ParentRef parent = folder.isHome() ? null : new FolderListing.ParentRef(
                folder.getParent().getId(), DisplayPath.of(mine, owner.getUsername(), folder.getParent().getPath()));

        // 최근 수정순이면 폴더도 그 안의 읽을 수 있는 글 중 가장 최근 것으로 줄 세운다. 글이 없으면 만든 날로
        Comparator<Folder> order = byUpdated
                ? Comparator.comparing((Folder child) -> latestOf(child, stats)).reversed().thenComparing(BY_NAME)
                : BY_NAME;
        List<FolderSummary> folders = children.stream()
                .sorted(order)
                .map(child -> new FolderSummary(child.getId(), child.getName(), child.getPath(),
                        DisplayPath.of(mine, owner.getUsername(), child.getPath()), stats.get(child.getId()).postCount(),
                        stats.get(child.getId()).folderCount(), child.getCreatedAt()))
                .toList();

        return new FolderListing(info, parent, folders,
                postService.listInFolder(viewerId, folder.getId(), byUpdated, cursor, size));
    }

    private static Instant latestOf(Folder folder, Map<Long, FolderStats> stats) {
        Instant lastUpdatedAt = stats.get(folder.getId()).lastUpdatedAt();
        return (lastUpdatedAt == null) ? folder.getCreatedAt() : lastUpdatedAt;
    }

}
