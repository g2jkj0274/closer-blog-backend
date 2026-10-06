package com.closer.blog.post.scheduler;

import com.closer.blog.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 휴지통에 30일 넘게 있던 글을 매일 지운다 (docs/erd.md 7절).
 */
@Component
@RequiredArgsConstructor
public class TrashPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeScheduler.class);

    private final PostService postService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    void purgeExpiredPosts() {
        int deleted = postService.purgeExpired();
        log.info("휴지통에서 30일이 지난 글 {}개를 지웠습니다.", deleted);
    }

}
