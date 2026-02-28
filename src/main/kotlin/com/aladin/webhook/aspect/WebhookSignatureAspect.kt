package com.aladin.webhook.aspect

import com.aladin.webhook.domain.exception.SignatureVerificationException
import com.aladin.webhook.util.HmacVerifier
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * @RequiresWebhookSignature 가 붙은 메서드에 대해 HMAC 서명 검증을 수행하는 Aspect.
 *
 * 실행 시점: Spring MVC 가 @RequestBody 를 resolve 한 이후(@Before) → body 가 args 에 이미 있음
 * 덕분에 body 를 재읽기 위한 CachedBodyFilter 없이 HMAC 을 검증할 수 있다.
 *
 * 검증 순서:
 *   1. X-Signature 헤더 존재 확인 (없으면 SignatureVerificationException → 401)
 *   2. X-Event-Id 형식 검사      (위반 시 IllegalArgumentException → 400)
 *   3. HMAC-SHA256 서명 비교      (불일치 시 SignatureVerificationException → 401)
 */
@Aspect
@Component
class WebhookSignatureAspect(
    private val hmacVerifier: HmacVerifier,
    @Value("\${webhook.secret}") private val secret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Before("@annotation(com.aladin.webhook.annotation.RequiresWebhookSignature)")
    fun validateSignature(joinPoint: JoinPoint) {
        val request =
            (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request

        // 1. X-Signature 헤더 — 없으면 서명 검증 실패로 간주 (401)
        val signature =
            request.getHeader("X-Signature")
                ?: throw SignatureVerificationException("Missing X-Signature header")

        // 2. X-Event-Id 형식 검사 — @RequestHeader 가 존재 여부를 앞서 보장
        val eventId = request.getHeader("X-Event-Id")!!
        validateEventId(eventId)

        // 3. @RequestBody 파라미터 위치를 리플렉션으로 탐색 → body 추출
        val methodSignature = joinPoint.signature as MethodSignature
        val bodyArgIndex =
            methodSignature.method.parameters
                .indexOfFirst { it.isAnnotationPresent(RequestBody::class.java) }
        val rawBody = joinPoint.args.getOrNull(bodyArgIndex) as? String ?: ""

        // 4. HMAC-SHA256 검증 (MessageDigest.isEqual — 타이밍 공격 방지)
        if (!hmacVerifier.verify(rawBody, signature, secret)) {
            log.warn("Signature verification failed. eventId={}", eventId)
            throw SignatureVerificationException("Invalid HMAC signature")
        }
    }

    private fun validateEventId(eventId: String) {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(eventId.length <= 255) { "eventId too long" }
        require(eventId.matches(Regex("^[a-zA-Z0-9\\-_]+$"))) {
            "eventId contains invalid characters"
        }
    }
}
