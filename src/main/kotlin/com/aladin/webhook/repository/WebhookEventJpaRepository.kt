package com.aladin.webhook.repository

import com.aladin.webhook.domain.WebhookEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WebhookEventJpaRepository : JpaRepository<WebhookEvent, Long> {
    fun findByEventId(eventId: String): WebhookEvent?

    @Modifying(clearAutomatically = true)
    @Query(
        value =
            "INSERT OR IGNORE INTO webhook_events (event_id, event_type, payload, status) VALUES (:eventId, :eventType, :payload, 'RECEIVED')",
        nativeQuery = true,
    )
    fun insertOrIgnore(
        @Param("eventId") eventId: String,
        @Param("eventType") eventType: String,
        @Param("payload") payload: String,
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE webhook_events SET status = :status, updated_at = strftime('%Y-%m-%d %H:%M:%S','now') WHERE event_id = :eventId",
        nativeQuery = true,
    )
    fun updateStatus(
        @Param("eventId") eventId: String,
        @Param("status") status: String,
    )

    @Modifying(clearAutomatically = true)
    @Query(
        value =
            "UPDATE webhook_events SET status = 'FAILED', error_message = :reason, updated_at = strftime('%Y-%m-%d %H:%M:%S','now') WHERE event_id = :eventId",
        nativeQuery = true,
    )
    fun updateFailed(
        @Param("eventId") eventId: String,
        @Param("reason") reason: String,
    )
}
