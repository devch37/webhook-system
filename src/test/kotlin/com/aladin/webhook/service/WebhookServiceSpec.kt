package com.aladin.webhook.service

import com.aladin.webhook.domain.dto.WebhookResult
import com.aladin.webhook.domain.enum.EventStatus
import com.aladin.webhook.repository.WebhookEventRepository
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["webhook.secret=test-webhook-secret-key-at-least-32"],
)
@ActiveProfiles("test")
class WebhookServiceSpec : DescribeSpec() {
    @Autowired lateinit var webhookService: WebhookService

    @Autowired lateinit var eventRepository: WebhookEventRepository

    init {
        describe("handle - 중복 상태별 처리") {
            it("PROCESSING 상태 이벤트 재전송 → WebhookResult.Processing") {
                val eventId = UUID.randomUUID().toString()
                val body = """{"accountKey":"proc_user","eventType":"ACCOUNT_DELETED","data":{}}"""
                eventRepository.insertIfNotExists(eventId, "ACCOUNT_DELETED", body)
                eventRepository.updateStatus(eventId, EventStatus.PROCESSING)

                val result = webhookService.handle(eventId, body)

                result.shouldBeInstanceOf<WebhookResult.Processing>()
                result.message shouldBe "처리 중"
            }

            it("RECEIVED 상태 이벤트 재전송 → WebhookResult.AlreadyProcessed (else 분기)") {
                val eventId = UUID.randomUUID().toString()
                val body = """{"accountKey":"recv_user","eventType":"ACCOUNT_DELETED","data":{}}"""
                eventRepository.insertIfNotExists(eventId, "ACCOUNT_DELETED", body)
                // 상태는 RECEIVED (기본값) 그대로 유지

                val result = webhookService.handle(eventId, body)

                result.shouldBeInstanceOf<WebhookResult.AlreadyProcessed>()
                result.message shouldBe "이미 처리됨"
            }
        }

        describe("validateConfig") {
            it("빈 시크릿 → IllegalStateException") {
                val mockRepo = Mockito.mock(WebhookEventRepository::class.java)
                val mockPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java)
                val service =
                    WebhookService(
                        IdempotencyLockManager(),
                        mockRepo,
                        mockPublisher,
                        ObjectMapper(),
                        "",
                    )
                shouldThrow<IllegalStateException> { service.validateConfig() }
            }
        }
    }
}
