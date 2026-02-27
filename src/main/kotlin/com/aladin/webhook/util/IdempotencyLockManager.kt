package com.aladin.webhook.util

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * eventId 기반 키 잠금으로 Race Condition 방어
 *
 * [현재] JVM 내 메모리 락 → 단일 인스턴스 환경
 * [확장] Redis Redisson RLock으로 교체 → 다중 인스턴스 환경
 *   val lock = redissonClient.getLock("idempotency:$eventId")
 *   lock.tryLock(3, 10, TimeUnit.SECONDS)
 *
 * 다중 인스턴스 운영 시 SQLite → PostgreSQL/MySQL 교체도 필요
 * (SQLite는 단일 파일 기반으로 다중 인스턴스 동시 쓰기 미지원)
 */
@Component
class IdempotencyLockManager {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> withLock(
        eventId: String,
        block: () -> T,
    ): T {
        val lock = locks.computeIfAbsent(eventId) { ReentrantLock() }
        return try {
            check(lock.tryLock(3, TimeUnit.SECONDS)) {
                "Lock timeout for eventId: $eventId"
            }
            block()
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
            locks.remove(eventId, lock) // 메모리 누수 방지
        }
    }
}
