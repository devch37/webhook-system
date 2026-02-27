package com.aladin.webhook.controller

import com.aladin.webhook.service.WebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks")
class WebhookController(private val webhookService: WebhookService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/account-changes")
    fun receive(
        @RequestHeader("X-Signature") signature: String,
        @RequestHeader("X-Event-Id") eventId: String,
        @RequestBody rawBody: String,
    ): ResponseEntity<Map<String, String>> {
        log.info("Webhook received. eventId={}", eventId)
        val result = webhookService.handle(signature, eventId, rawBody)
        return ResponseEntity.ok(mapOf("message" to result.message))
    }
}
