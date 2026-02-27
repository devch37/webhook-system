package com.aladin.webhook.service

import com.aladin.webhook.domain.dto.WebhookResult
import com.aladin.webhook.domain.enum.EventStatus
import com.aladin.webhook.repository.WebhookEventRepository
import com.aladin.webhook.util.HmacVerifier
import com.aladin.webhook.util.IdempotencyLockManager
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["webhook.secret=test-webhook-secret-key-at-least-32"],
)
@ActiveProfiles("test")
class WebhookServiceSpec : DescribeSpec() {
    @Autowired lateinit var webhookService: WebhookService

    @Autowired lateinit var eventRepository: WebhookEventRepository

    private val secret = "test-webhook-secret-key-at-least-32"

    private fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    init {
        describe("handle - 중복 상태별 처리") {
            it("PROCESSING 상태 이벤트 재전송 → WebhookResult.Processing") {
                val eventId = UUID.randomUUID().toString()
                val body = """{"accountKey":"proc_user","eventType":"ACCOUNT_DELETED","data":{}}"""
                eventRepository.insertIfNotExists(eventId, "ACCOUNT_DELETED", body)
                eventRepository.updateStatus(eventId, EventStatus.PROCESSING)

                val result = webhookService.handle(sign(body), eventId, body)

                result.shouldBeInstanceOf<WebhookResult.Processing>()
                result.message shouldBe "처리 중"
            }

            it("RECEIVED 상태 이벤트 재전송 → WebhookResult.AlreadyProcessed (else 분기)") {
                val eventId = UUID.randomUUID().toString()
                val body = """{"accountKey":"recv_user","eventType":"ACCOUNT_DELETED","data":{}}"""
                eventRepository.insertIfNotExists(eventId, "ACCOUNT_DELETED", body)
                // 상태는 RECEIVED (기본값) 그대로 유지

                val result = webhookService.handle(sign(body), eventId, body)

                result.shouldBeInstanceOf<WebhookResult.AlreadyProcessed>()
                result.message shouldBe "이미 처리됨"
            }
        }

        describe("validateConfig") {
            it("빈 시크릿 → IllegalStateException") {
                val mockRepo = Mockito.mock(WebhookEventRepository::class.java)
                val mockAccSvc = Mockito.mock(AccountService::class.java)
                val service =
                    WebhookService(
                        HmacVerifier(),
                        IdempotencyLockManager(),
                        mockRepo,
                        mockAccSvc,
                        ObjectMapper(),
                        "",
                    )
                shouldThrow<IllegalStateException> { service.validateConfig() }
            }
        }
    }
}
