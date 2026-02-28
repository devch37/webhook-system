package com.aladin.webhook.annotation

/**
 * 이 어노테이션이 붙은 컨트롤러 메서드에만 Webhook HMAC 서명 검증을 적용한다.
 * [WebhookSignatureAspect] 가 @Before 어드바이스로 다음 항목을 검사한다:
 *   1. X-Signature 헤더 존재 여부 (없으면 401)
 *   2. X-Event-Id 형식 (공백·길이·허용 문자)
 *   3. HMAC-SHA256 서명 일치 여부 (불일치 시 401)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresWebhookSignature
