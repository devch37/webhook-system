package com.aladin.webhook.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacVerifierSpec :
    BehaviorSpec({

        val verifier = HmacVerifier()
        val fixedTimestamp = "1700000000" // 고정 타임스탬프 (단위 테스트용)

        fun sign(
            payload: String,
            secret: String,
            timestamp: String = fixedTimestamp,
        ): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return mac.doFinal("$timestamp.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
        }

        given("올바른 시크릿") {
            val payload = """{"accountKey":"user","eventType":"ACCOUNT_DELETED"}"""
            val secret = "my-secret"

            `when`("올바른 서명으로 검증") {
                then("true 반환") {
                    verifier.verify(payload, sign(payload, secret), secret, fixedTimestamp) shouldBe true
                }
            }
            `when`("잘못된 서명으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, "wrong-sig", secret, fixedTimestamp) shouldBe false
                }
            }
            `when`("빈 서명으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, "", secret, fixedTimestamp) shouldBe false
                }
            }
            `when`("다른 시크릿으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, sign(payload, secret), "other-secret", fixedTimestamp) shouldBe false
                }
            }
            `when`("타임스탬프가 다른 경우 검증") {
                then("false 반환 (타임스탬프가 서명 대상에 포함됨)") {
                    val sig = sign(payload, secret, fixedTimestamp)
                    verifier.verify(payload, sig, secret, "9999999999") shouldBe false
                }
            }
        }

        given("빈 페이로드") {
            val payload = ""
            val secret = "my-secret"

            `when`("빈 payload의 올바른 서명으로 검증") {
                then("true 반환 (빈 body도 유효한 HMAC 대상)") {
                    verifier.verify(payload, sign(payload, secret), secret, fixedTimestamp) shouldBe true
                }
            }
            `when`("빈 payload에 잘못된 서명으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, "wrong", secret, fixedTimestamp) shouldBe false
                }
            }
        }

        given("유니코드(한국어) 페이로드") {
            val payload = """{"accountKey":"사용자","eventType":"ACCOUNT_DELETED"}"""
            val secret = "my-secret"

            `when`("올바른 서명으로 검증") {
                then("UTF-8 인코딩 일관성 보장 — true 반환") {
                    verifier.verify(payload, sign(payload, secret), secret, fixedTimestamp) shouldBe true
                }
            }
            `when`("동일 payload를 다른 인코딩으로 서명한 경우") {
                then("false 반환") {
                    // Latin-1로 인코딩된 시그니처는 UTF-8 검증과 불일치
                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
                    val latin1Sig =
                        mac
                            .doFinal(
                                "$fixedTimestamp.$payload".toByteArray(Charsets.ISO_8859_1),
                            ).joinToString("") { "%02x".format(it) }
                    verifier.verify(payload, latin1Sig, secret, fixedTimestamp) shouldBe false
                }
            }
        }
    })
