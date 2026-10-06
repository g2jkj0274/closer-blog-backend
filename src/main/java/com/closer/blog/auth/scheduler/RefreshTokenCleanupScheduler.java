package com.closer.blog.auth.scheduler;

import com.closer.blog.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 리프레시 토큰을 매일 지운다 (docs/erd.md 7절).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    void deleteExpiredTokens() {
        int deleted = refreshTokenService.deleteExpired();
        log.info("만료된 리프레시 토큰 {}개를 지웠습니다.", deleted);
    }

}