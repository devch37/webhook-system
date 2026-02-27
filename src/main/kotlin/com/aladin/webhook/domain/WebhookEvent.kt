package com.aladin.webhook.domain

import com.aladin.webhook.domain.enum.EventStatus

data class WebhookEvent(
    val id: Long = 0,
    val eventId: String,
    val eventType: String,
    val payload: String,
    val status: EventStatus = EventStatus.RECEIVED,
    val errorMessage: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)
