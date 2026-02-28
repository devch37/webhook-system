package com.aladin.webhook.service

import com.aladin.webhook.domain.WebhookEvent
import com.aladin.webhook.domain.dto.WebhookRequest
import com.aladin.webhook.domain.dto.WebhookResult
import com.aladin.webhook.domain.enum.EventStatus
import com.aladin.webhook.domain.exception.NotFoundException
import com.aladin.webhook.repository.WebhookEventRepository
import com.aladin.webhook.util.IdempotencyLockManager
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookService(
    private val lockManager: IdempotencyLockManager,
    private val eventRepository: WebhookEventRepository,
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
    @Value("\${webhook.secret}") private val secret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findEvent(eventId: String): WebhookEvent =
        eventRepository.findByEventId(eventId)
            ?: throw NotFoundException("Event not found: $eventId")

    @PostConstruct
    fun validateConfig() {
        check(secret.length >= 32) { "WEBHOOK_SECRET must be at least 32 characters" }
    }

    /**
     * 처리 흐름 (서명·헤더 검증은 WebhookSignatureAspect 가 @Before 로 선처리):
     * 1. eventId 키 잠금 (Race Condition 방어)
     * 2. Idempotency 체크 (DB UNIQUE + 앱 레벨 이중 방어)
     * 3. RECEIVED → PROCESSING → DONE | FAILED 상태 전이
     */
    @Transactional
    fun handle(
        eventId: String,
        rawBody: String,
    ): WebhookResult =
        lockManager.withLock(eventId) {
            processWithIdempotency(eventId, rawBody)
        }

    private fun processWithIdempotency(
        eventId: String,
        rawBody: String,
    ): WebhookResult {
        val request = parseRequest(rawBody)

        val isNew = eventRepository.insertIfNotExists(eventId, request.eventType, rawBody)
        if (!isNew) {
            val existing = eventRepository.findByEventId(eventId)
            log.info("Duplicate event. eventId={}, status={}", eventId, existing?.status)
            return when (existing?.status) {
                EventStatus.DONE -> WebhookResult.AlreadyProcessed("이미 처리됨")
                EventStatus.PROCESSING -> WebhookResult.Processing("처리 중")
                EventStatus.FAILED -> WebhookResult.AlreadyProcessed("이미 처리됨 (실패)")
                else -> WebhookResult.AlreadyProcessed("이미 처리됨")
            }
        }

        eventRepository.updateStatus(eventId, EventStatus.PROCESSING)

        return try {
            accountService.process(request)
            eventRepository.updateStatus(eventId, EventStatus.DONE)
            log.info("Event done. eventId={}, type={}", eventId, request.eventType)
            WebhookResult.Accepted("처리됨")
        } catch (e: Exception) {
            eventRepository.updateFailed(eventId, e.message ?: "Unknown error")
            log.error("Event failed. eventId={}, error={}", eventId, e.message)
            WebhookResult.Accepted("처리됨") // 외부 재전송 루프 방지
        }
    }

    private fun parseRequest(rawBody: String): WebhookRequest =
        runCatching { objectMapper.readValue(rawBody, WebhookRequest::class.java) }
            .getOrElse { throw IllegalArgumentException("Invalid payload: ${it.message}") }
}
