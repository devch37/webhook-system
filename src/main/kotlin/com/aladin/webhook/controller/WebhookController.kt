package com.aladin.webhook.controller

import com.aladin.webhook.annotation.RequiresWebhookSignature
import com.aladin.webhook.domain.dto.WebhookResult
import com.aladin.webhook.service.WebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhooks")
class WebhookController(
    private val webhookService: WebhookService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 신규 이벤트: 202 Accepted (비동기 처리 큐에 적재됨)
     * 중복 이벤트: 200 OK (이미 처리됨 / 처리 중)
     */
    @PostMapping("/account-changes")
    @RequiresWebhookSignature // Aspect 가 서명·eventId 형식 검증
    fun receive(
        @RequestHeader("X-Event-Id") eventId: String, // 존재 여부는 Spring 이 400 처리
        @RequestBody rawBody: String,
    ): ResponseEntity<Map<String, String>> {
        log.info("Webhook received. eventId={}", eventId)
        val result = webhookService.handle(eventId, rawBody)
        val status = if (result is WebhookResult.Queued) HttpStatus.ACCEPTED else HttpStatus.OK
        return ResponseEntity.status(status).body(mapOf("message" to result.message))
    }
}
