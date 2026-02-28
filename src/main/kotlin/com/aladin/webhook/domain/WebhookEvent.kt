package com.aladin.webhook.domain

import com.aladin.webhook.domain.enum.EventStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "webhook_events")
data class WebhookEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "event_id", unique = true, nullable = false)
    val eventId: String,
    @Column(name = "event_type", nullable = false)
    val eventType: String,
    @Column(nullable = false)
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: EventStatus = EventStatus.RECEIVED,
    @Column(name = "error_message")
    val errorMessage: String?,
    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", insertable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
