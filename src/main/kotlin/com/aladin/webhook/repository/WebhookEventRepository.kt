package com.aladin.webhook.repository

import com.aladin.webhook.domain.WebhookEvent
import com.aladin.webhook.domain.enum.EventStatus
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class WebhookEventRepository(
    private val webhookEventJpaRepository: WebhookEventJpaRepository,
) {
    /** true = 신규 저장, false = 중복
     * INSERT OR IGNORE로 원자적 처리 — Race Condition 방어 */
    @Transactional
    fun insertIfNotExists(
        eventId: String,
        eventType: String,
        payload: String,
    ): Boolean = webhookEventJpaRepository.insertOrIgnore(eventId, eventType, payload) > 0

    fun findByEventId(eventId: String): WebhookEvent? = webhookEventJpaRepository.findByEventId(eventId)

    @Transactional
    fun updateStatus(
        eventId: String,
        status: EventStatus,
    ) {
        val event = webhookEventJpaRepository.findByEventId(eventId) ?: return
        webhookEventJpaRepository.save(event.copy(status = status))
    }

    @Transactional
    fun updateFailed(
        eventId: String,
        reason: String,
    ) {
        val event = webhookEventJpaRepository.findByEventId(eventId) ?: return
        webhookEventJpaRepository.save(event.copy(status = EventStatus.FAILED, errorMessage = reason.take(1000)))
    }
}
