package com.aladin.webhook.controller

import com.aladin.webhook.controller.dto.response.EventResponse
import com.aladin.webhook.controller.dto.response.toResponse
import com.aladin.webhook.service.WebhookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/inbox")
class InboxController(
    private val webhookService: WebhookService,
) {
    @GetMapping("/events/{eventId}")
    fun getEvent(
        @PathVariable eventId: String,
    ): ResponseEntity<EventResponse> {
        val event = webhookService.findEvent(eventId)
        return ResponseEntity.ok(event.toResponse())
    }
}
