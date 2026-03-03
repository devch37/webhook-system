package com.aladin.webhook.service

import com.aladin.webhook.domain.event.WebhookReceivedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * WebhookReceivedEvent 를 수신하여 비동기 처리로 위임하는 브릿지.
 *
 * insertIfNotExists 는 자체 @Transactional 로 즉시 커밋되므로
 * publishEvent 호출 시점에 이미 RECEIVED 행이 DB 에 존재한다.
 * 따라서 AFTER_COMMIT 없이 @EventListener 로도 안전하다.
 *
 * @TransactionalEventListener 와 @Async 를 같은 메서드에 두면 Spring 이 이벤트 호출 시
 * 프록시를 거치지 않아 @Async 가 동작하지 않는다.
 * 두 역할을 분리:
 *   - 이 클래스: 이벤트 수신 후 processor 에 위임 (동기, 같은 스레드)
 *   - WebhookEventProcessor: @Async 를 통해 별도 스레드에서 실제 처리
 */
@Component
class WebhookEventListener(
    private val processor: WebhookEventProcessor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onWebhookReceived(event: WebhookReceivedEvent) {
        log.debug("Event received, dispatching async processing. eventId={}", event.eventId)
        processor.processAsync(event.eventId, event.rawBody)
    }
}
