package com.aladin.webhook.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 비동기 Webhook 이벤트 처리를 위한 전용 스레드 풀 설정.
 *
 * - corePoolSize(4): 평상시 유지 스레드 수
 * - maxPoolSize(16): 순간 급증 시 최대 스레드 수
 * - queueCapacity(500): 큐 초과 전 대기 가능 이벤트 수
 *
 * 선택 배경: 저장 후 비동기 처리 방식을 선택 (명세 2-3)
 * → Webhook 요청을 즉시 202 Accepted 로 반환하고, 실제 계정 처리는 별도 스레드에서 수행
 */
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean("webhookExecutor")
    fun webhookTaskExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 16
            queueCapacity = 500
            setThreadNamePrefix("webhook-async-")
            initialize()
        }
}
