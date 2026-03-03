package com.aladin.webhook.domain.event

/**
 * Webhook 이벤트가 RECEIVED 상태로 저장된 직후 발행되는 Spring ApplicationEvent.
 *
 * WebhookService 는 이 이벤트를 발행만 하고, 누가 처리하는지 알지 못한다.
 * WebhookEventProcessor 가 @TransactionalEventListener(AFTER_COMMIT) 으로 수신한다.
 *
 * AFTER_COMMIT 을 사용하면 발행 트랜잭션이 커밋된 후에만 리스너가 실행되므로
 * DB에 RECEIVED 행이 확실히 존재한다는 것을 보장한다.
 */
data class WebhookReceivedEvent(
    val eventId: String,
    val rawBody: String,
)
