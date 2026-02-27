package com.aladin.webhook.repository

import com.aladin.webhook.domain.EventStatus
import com.aladin.webhook.domain.WebhookEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class WebhookEventRepository(private val jdbc: JdbcTemplate) {

    /** true = 신규 저장, false = 중복 */
    fun insertIfNotExists(eventId: String, eventType: String, payload: String): Boolean =
        jdbc.update("""
            INSERT OR IGNORE INTO webhook_events (event_id, event_type, payload, status)
            VALUES (?, ?, ?, 'RECEIVED')
        """, eventId, eventType, payload) > 0

    fun findByEventId(eventId: String): WebhookEvent? =
        runCatching {
            jdbc.queryForObject(
                "SELECT * FROM webhook_events WHERE event_id = ?",
                rowMapper, eventId
            )
        }.getOrNull()

    fun updateStatus(eventId: String, status: EventStatus) {
        jdbc.update("""
            UPDATE webhook_events
            SET status = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
            WHERE event_id = ?
        """, status.name, eventId)
    }

    fun updateFailed(eventId: String, reason: String) {
        jdbc.update("""
            UPDATE webhook_events
            SET status = 'FAILED',
                error_message = ?,
                updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
            WHERE event_id = ?
        """, reason.take(1000), eventId)
    }

    private val rowMapper = RowMapper { rs, _ ->
        WebhookEvent(
            id           = rs.getLong("id"),
            eventId      = rs.getString("event_id"),
            eventType    = rs.getString("event_type"),
            payload      = rs.getString("payload"),
            status       = EventStatus.valueOf(rs.getString("status")),
            errorMessage = rs.getString("error_message"),
            createdAt    = rs.getString("created_at"),
            updatedAt    = rs.getString("updated_at"),
        )
    }
}
