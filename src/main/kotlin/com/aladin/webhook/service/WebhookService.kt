package com.aladin.webhook.service

import com.aladin.webhook.domain.WebhookEvent
import com.aladin.webhook.domain.dto.WebhookRequest
import com.aladin.webhook.domain.dto.WebhookResult
import com.aladin.webhook.domain.enum.EventStatus
import com.aladin.webhook.domain.event.WebhookReceivedEvent
import com.aladin.webhook.domain.exception.NotFoundException
import com.aladin.webhook.repository.WebhookEventRepository
import com.aladin.webhook.util.IdempotencyLockManager
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class WebhookService(
    private val lockManager: IdempotencyLockManager,
    private val eventRepository: WebhookEventRepository,
    private val eventPublisher: ApplicationEventPublisher,
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
     * 3. 신규 이벤트 → insertIfNotExists(자체 @Transactional, 즉시 커밋) 후 이벤트 발행
     *    WebhookEventListener 가 수신 → processor.processAsync() 비동기 처리 시작
     * 4. 중복 이벤트 → 현재 상태 기반 응답 → 200 OK
     */
    fun handle(
        eventId: String,
        rawBody: String,
    ): WebhookResult =
        lockManager.withLock(eventId) {
            val request = parseRequest(rawBody)

            val isNew = eventRepository.insertIfNotExists(eventId, request.eventType, rawBody)
            if (!isNew) {
                val existing = eventRepository.findByEventId(eventId)
                log.info("Duplicate event. eventId={}, status={}", eventId, existing?.status)
                return@withLock when (existing?.status) {
                    EventStatus.DONE -> WebhookResult.AlreadyProcessed("이미 처리됨")
                    EventStatus.PROCESSING -> WebhookResult.Processing("처리 중")
                    EventStatus.FAILED -> WebhookResult.AlreadyProcessed("이미 처리됨 (실패)")
                    else -> WebhookResult.AlreadyProcessed("이미 처리됨")
                }
            }

            // 트랜잭션 커밋 후 비동기 처리 이벤트 발행
            // (AFTER_COMMIT 리스너가 DB에 RECEIVED 행이 존재함을 보장받고 처리 시작)
            eventPublisher.publishEvent(WebhookReceivedEvent(eventId, rawBody))
            log.info("Event received and published. eventId={}", eventId)
            WebhookResult.Queued("수신됨")
        }

    private fun parseRequest(rawBody: String): WebhookRequest =
        runCatching { objectMapper.readValue(rawBody, WebhookRequest::class.java) }
            .getOrElse { throw IllegalArgumentException("Invalid payload: ${it.message}") }
}
