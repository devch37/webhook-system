package com.aladin.webhook.domain.dto

sealed class WebhookResult(
    val message: String,
) {
    /** 신규 이벤트: 비동기 처리 큐에 적재됨 → 202 Accepted */
    class Queued(
        message: String,
    ) : WebhookResult(message)

    class AlreadyProcessed(
        message: String,
    ) : WebhookResult(message)

    class Processing(
        message: String,
    ) : WebhookResult(message)
}
