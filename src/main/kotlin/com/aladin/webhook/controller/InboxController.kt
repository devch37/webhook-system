package com.aladin.webhook.controller

import com.aladin.webhook.domain.WebhookEvent
import com.aladin.webhook.service.WebhookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

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

data class EventResponse(
    val eventId: String,
    val eventType: String,
    val status: String,
    val errorMessage: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun WebhookEvent.toResponse() = EventResponse(eventId, eventType, status.name, errorMessage, createdAt, updatedAt)
