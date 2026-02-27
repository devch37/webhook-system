package com.aladin.webhook.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class IdempotencyLockManagerSpec : DescribeSpec({

    val lockManager = IdempotencyLockManager()

    describe("withLock") {
        it("정상 실행 → 결과 반환") {
            val result = lockManager.withLock("normal-key") { "ok" }
            result shouldBe "ok"
        }

        it("락 획득 타임아웃 → IllegalStateException") {
            val lockKey = "timeout-${UUID.randomUUID()}"
            val latch1 = CountDownLatch(1)  // lock held signal
            val latch2 = CountDownLatch(1)  // release signal

            val bgThread = Thread {
                lockManager.withLock(lockKey) {
                    latch1.countDown()             // 락 보유 알림
                    latch2.await(10, TimeUnit.SECONDS)  // 락 유지
                }
            }

            bgThread.start()
            latch1.await()  // 백그라운드 스레드가 락 보유할 때까지 대기

            try {
                shouldThrow<IllegalStateException> {
                    lockManager.withLock(lockKey) { "should timeout" }
                }
            } finally {
                latch2.countDown()   // 백그라운드 스레드 해제
                bgThread.join(5000)
            }
        }
    }
})
