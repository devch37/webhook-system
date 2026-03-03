package com.aladin.webhook.service

import com.aladin.webhook.domain.dto.WebhookRequest
import com.aladin.webhook.domain.enum.EventStatus
import com.aladin.webhook.repository.WebhookEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Webhook 이벤트 비동기 처리기.
 *
 * WebhookEventListener 의 @TransactionalEventListener(AFTER_COMMIT) 을 통해 호출된다.
 * 즉, 이 메서드가 실행되는 시점에는 발행 트랜잭션이 이미 커밋되어
 * DB에 RECEIVED 행이 확실히 존재함이 보장된다.
 *
 * 처리 흐름: RECEIVED → PROCESSING → DONE | FAILED
 */
@Component
class WebhookEventProcessor(
    private val eventRepository: WebhookEventRepository,
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("webhookExecutor")
    fun processAsync(
        eventId: String,
        rawBody: String,
    ) {
        eventRepository.updateStatus(eventId, EventStatus.PROCESSING)
        try {
            val request = parseRequest(rawBody)
            accountService.process(request)
            eventRepository.updateStatus(eventId, EventStatus.DONE)
            log.info("Event done. eventId={}", eventId)
        } catch (e: Exception) {
            eventRepository.updateFailed(eventId, e.message ?: "Unknown error")
            log.error("Event failed. eventId={}, error={}", eventId, e.message)
        }
    }

    private fun parseRequest(rawBody: String): WebhookRequest =
        runCatching { objectMapper.readValue(rawBody, WebhookRequest::class.java) }
            .getOrElse { throw IllegalArgumentException("Invalid payload: ${it.message}") }
}
