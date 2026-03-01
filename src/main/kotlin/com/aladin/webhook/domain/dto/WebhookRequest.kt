package com.aladin.webhook.domain.dto

data class WebhookRequest(
    val accountKey: String,
    val eventType: String,
    val data: Map<String, Any> = emptyMap(),
)