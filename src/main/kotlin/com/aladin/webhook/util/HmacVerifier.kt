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
     * HMAC-SHA256 서명 검증 (Stripe 스타일 Replay Attack 방지)
     *
     * 서명 대상: "$timestamp.$payload"
     * - timestamp 포함 서명 → 타임스탬프 변조 시 서명 불일치
     * - 원본 타임스탬프는 max-age 이후 만료 → 재전송(Replay) 차단
     * - MessageDigest.isEqual() 사용 → 타이밍 공격(Timing Attack) 방지
     */
    fun verify(
        payload: String,
        signature: String,
        secret: String,
        timestamp: String,
    ): Boolean =
        runCatching {
            val mac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256_ALGORITHM))
            val expected =
                mac
                    .doFinal("$timestamp.$payload".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                signature.toByteArray(Charsets.UTF_8),
            )
        }.onFailure { log.warn("HMAC verification error: ${it.message}") }
            .getOrDefault(false)

    companion object {
        private const val HMAC_SHA256_ALGORITHM = "HmacSHA256"
    }
}
