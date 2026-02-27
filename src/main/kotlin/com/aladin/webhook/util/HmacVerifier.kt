package com.aladin.webhook.util

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 타이밍 공격(Timing Attack) 방지
     * - 일반 문자열 == 비교: 첫 불일치 시 즉시 종료 → 응답 시간으로 시크릿 추론 가능
     * - MessageDigest.isEqual(): 항상 전체 길이 비교 → 상수 시간 보장
     */
    fun verify(payload: String, signature: String, secret: String): Boolean =
        runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val expected = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                signature.toByteArray(Charsets.UTF_8),
            )
        }.onFailure { log.warn("HMAC verification error: ${it.message}") }
         .getOrDefault(false)
}
