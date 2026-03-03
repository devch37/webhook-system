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
import java.time.Instant

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
    @Value("\${webhook.signature.max-age-minutes:15}") private val maxAgeMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 검증 순서:
     *   1. X-Signature 헤더 존재 확인  (없으면 SignatureVerificationException → 401)
     *   2. X-Timestamp 헤더 존재 확인  (없으면 SignatureVerificationException → 401)
     *   3. 타임스탬프 유효 시간 검증    (만료/미래 시간 → SignatureVerificationException → 401)
     *   4. X-Event-Id 형식 검사        (위반 시 IllegalArgumentException → 400)
     *   5. HMAC-SHA256 서명 비교       (불일치 시 SignatureVerificationException → 401)
     *      서명 대상: "$timestamp.$body" — 타임스탬프 변조 및 Replay Attack 방지
     */
    @Before("@annotation(com.aladin.webhook.annotation.RequiresWebhookSignature)")
    fun validateSignature(joinPoint: JoinPoint) {
        val request =
            (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request

        // 1. X-Signature 헤더 — 없으면 401
        val signature =
            request.getHeader("X-Signature")
                ?: throw SignatureVerificationException("Missing X-Signature header")

        // 2. X-Timestamp 헤더 — 없으면 401
        val timestamp =
            request.getHeader("X-Timestamp")
                ?: throw SignatureVerificationException("Missing X-Timestamp header")

        // 3. 타임스탬프 유효성 및 만료 검증
        validateTimestamp(timestamp)

        // 4. X-Event-Id 형식 검사 — @RequestHeader 가 존재 여부를 앞서 보장
        val eventId = request.getHeader("X-Event-Id")!!
        validateEventId(eventId)

        // 5. @RequestBody 파라미터 위치를 리플렉션으로 탐색 → body 추출
        val methodSignature = joinPoint.signature as MethodSignature
        val bodyArgIndex =
            methodSignature.method.parameters
                .indexOfFirst { it.isAnnotationPresent(RequestBody::class.java) }
        val rawBody = joinPoint.args.getOrNull(bodyArgIndex) as? String ?: ""

        // 6. HMAC-SHA256 검증: HMAC(secret, "$timestamp.$body") — 타이밍 공격 방지
        if (!hmacVerifier.verify(rawBody, signature, secret, timestamp)) {
            log.warn("Signature verification failed. eventId={}", eventId)
            throw SignatureVerificationException("Invalid HMAC signature")
        }
    }

    private fun validateTimestamp(timestamp: String) {
        val epochSeconds =
            timestamp.toLongOrNull()
                ?: throw SignatureVerificationException("Invalid X-Timestamp format")
        val ageSeconds = Instant.now().epochSecond - epochSeconds
        // 미래 5분 초과(클럭 스큐 허용) 또는 max-age 초과 시 거부
        if (ageSeconds < -300 || ageSeconds > maxAgeMinutes * 60) {
            log.warn("Timestamp out of valid window. age={}s, maxAge={}min", ageSeconds, maxAgeMinutes)
            throw SignatureVerificationException("Request timestamp expired or future-dated")
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
