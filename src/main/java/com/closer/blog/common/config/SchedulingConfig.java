package com.closer.blog.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 예약 작업을 켠다. 서버 한 대를 전제한다 (docs/tech-stack.md 3.6절).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
