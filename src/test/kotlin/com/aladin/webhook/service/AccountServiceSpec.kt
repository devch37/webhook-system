package com.aladin.webhook.service

import com.aladin.webhook.domain.dto.WebhookRequest
import com.aladin.webhook.domain.enum.AccountStatus
import com.aladin.webhook.domain.enum.EventStatus
import com.aladin.webhook.repository.AccountRepository
import com.aladin.webhook.repository.WebhookEventRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["webhook.secret=test-webhook-secret-key-at-least-32"],
)
@ActiveProfiles("test")
class AccountServiceSpec : DescribeSpec() {
    @Autowired lateinit var accountService: AccountService

    @Autowired lateinit var accountRepository: AccountRepository

    @Autowired lateinit var webhookEventRepository: WebhookEventRepository

    init {
        describe("process - 이벤트 타입별 계정 처리") {
            it("EMAIL_FORWARDING_CHANGED → email 갱신") {
                val req = WebhookRequest("svc_email_01", "EMAIL_FORWARDING_CHANGED", mapOf("email" to "svc@test.com"))
                accountService.process(req)

                val account = accountRepository.findByAccountKey("svc_email_01")
                account shouldNotBe null
                account?.email shouldBe "svc@test.com"
                account?.status shouldBe AccountStatus.ACTIVE
            }

            it("ACCOUNT_DELETED → status DELETED") {
                val req = WebhookRequest("svc_del_01", "ACCOUNT_DELETED")
                accountService.process(req)

                val account = accountRepository.findByAccountKey("svc_del_01")
                account?.status shouldBe AccountStatus.DELETED
            }

            it("APPLE_ACCOUNT_DELETED → status APPLE_DELETED") {
                val req = WebhookRequest("svc_apple_01", "APPLE_ACCOUNT_DELETED")
                accountService.process(req)

                val account = accountRepository.findByAccountKey("svc_apple_01")
                account?.status shouldBe AccountStatus.APPLE_DELETED
            }

            it("알 수 없는 eventType → IllegalArgumentException") {
                val req = WebhookRequest("svc_unknown_01", "UNKNOWN_TYPE")
                shouldThrow<IllegalArgumentException> { accountService.process(req) }
            }

            it("accountKey 255자 초과 → IllegalArgumentException") {
                val longKey = "k".repeat(256)
                val req = WebhookRequest(longKey, "ACCOUNT_DELETED")
                shouldThrow<IllegalArgumentException> { accountService.process(req) }
            }

            it("EMAIL_FORWARDING_CHANGED에 email 누락 → IllegalArgumentException") {
                val req = WebhookRequest("svc_no_email_01", "EMAIL_FORWARDING_CHANGED")
                shouldThrow<IllegalArgumentException> { accountService.process(req) }
            }

            it("EMAIL_FORWARDING_CHANGED에 잘못된 이메일 형식 → IllegalArgumentException") {
                val req = WebhookRequest("svc_bad_email_01", "EMAIL_FORWARDING_CHANGED", mapOf("email" to "invalid-email"))
                shouldThrow<IllegalArgumentException> { accountService.process(req) }
            }

            it("계정 없어도 upsert 후 처리 (account 자동 생성)") {
                val req = WebhookRequest("svc_new_acct_01", "ACCOUNT_DELETED")
                accountService.process(req) // 기존 계정 없어도 예외 없이 처리

                val account = accountRepository.findByAccountKey("svc_new_acct_01")
                account shouldNotBe null
                account?.status shouldBe AccountStatus.DELETED
            }
        }

        describe("AccountRepository - 존재하지 않는 키 조작 (?: return 브랜치)") {
            it("updateEmail: 없는 accountKey → 예외 없이 무시") {
                // null 반환 → ?: return 브랜치 커버
                accountRepository.updateEmail("nonexistent-key-xyz", "test@test.com")
            }

            it("updateStatus: 없는 accountKey → 예외 없이 무시") {
                accountRepository.updateStatus("nonexistent-key-xyz", AccountStatus.DELETED)
            }
        }

        describe("WebhookEventRepository - 존재하지 않는 eventId 조작 (?: return 브랜치)") {
            it("updateStatus: 없는 eventId → 예외 없이 무시") {
                webhookEventRepository.updateStatus("nonexistent-event-xyz", EventStatus.DONE)
            }

            it("updateFailed: 없는 eventId → 예외 없이 무시") {
                webhookEventRepository.updateFailed("nonexistent-event-xyz", "some error")
            }
        }
    }
}
