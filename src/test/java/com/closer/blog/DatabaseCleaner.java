package com.closer.blog;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 사이에 모든 테이블을 비운다. 외래 키 순서를 신경 쓰지 않도록 TRUNCATE ... CASCADE를 쓴다.
 */
public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "TRUNCATE post_tags, posts, tags, folders, refresh_tokens, users RESTART IDENTITY CASCADE");
    }

}
/**
 * 이번 테스트는 처음으로 posts 행을 넣습니다.
 * 기존 테스트들은 folderRepository.deleteAllInBatch()로 정리하는데,
 * 글이 남아 있으면 외래 키 때문에 폴더 삭제가 실패합니다.
 * 그러면 다른 테스트 클래스가 연쇄로 깨집니다.
 * 그래서 모든 테이블을 한 번에 비우는 도우미를 만들고,
 * FolderTest는 시작할 때와 끝날 때 모두 이것을 부릅니다.
 * 기존 테스트들도 나중에 이 도우미로 바꾸면 정리 코드가 한 줄로 줄어듭니다
 */