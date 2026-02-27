package com.aladin.webhook.domain.enum

enum class EventType(
    private val description: String,
) {
    EMAIL_FORWARDING_CHANGED(
        description = "EMAIL 변경 요청",
    ),
    ACCOUNT_DELETED(
        description = "계정 삭제 요청",
    ),
    APPLE_ACCOUNT_DELETED(
        description = "APPLE 계정 삭제 요청",
    ),
    ;

    companion object {
        fun from(value: String) =
            entries.find { it.name == value }
                ?: throw IllegalArgumentException("Unknown event type: $value")
    }
}
