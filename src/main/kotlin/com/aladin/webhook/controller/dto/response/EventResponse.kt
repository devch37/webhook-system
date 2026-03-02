package com.aladin.webhook.controller.dto.response

import com.aladin.webhook.domain.WebhookEvent
import java.time.LocalDateTime

data class EventResponse(
    val eventId: String,
    val eventType: String,
    val status: String,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun WebhookEvent.toResponse() = EventResponse(eventId, eventType, status.name, errorMessage, createdAt, updatedAt)