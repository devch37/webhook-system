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
}
