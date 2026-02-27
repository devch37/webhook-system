package com.aladin.webhook.domain

enum class EventType {
    EMAIL_FORWARDING_CHANGED,
    ACCOUNT_DELETED,
    APPLE_ACCOUNT_DELETED;

    companion object {
        fun from(value: String) = entries.find { it.name == value }
            ?: throw IllegalArgumentException("Unknown event type: $value")
    }
}

enum class EventStatus { RECEIVED, PROCESSING, DONE, FAILED }

enum class AccountStatus { ACTIVE, DELETED, APPLE_DELETED }
