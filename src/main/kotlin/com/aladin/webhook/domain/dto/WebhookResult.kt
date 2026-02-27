package com.aladin.webhook.domain.dto

sealed class WebhookResult(val message: String) {
    class Accepted(message: String)         : WebhookResult(message)
    class AlreadyProcessed(message: String) : WebhookResult(message)
    class Processing(message: String)       : WebhookResult(message)
}
