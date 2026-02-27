package com.aladin.webhook.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacVerifierSpec :
    BehaviorSpec({

        val verifier = HmacVerifier()

        fun sign(
            payload: String,
            secret: String,
        ): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        given("올바른 시크릿") {
            val payload = """{"accountKey":"user","eventType":"ACCOUNT_DELETED"}"""
            val secret = "my-secret"

            `when`("올바른 서명으로 검증") {
                then("true 반환") {
                    verifier.verify(payload, sign(payload, secret), secret) shouldBe true
                }
            }
            `when`("잘못된 서명으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, "wrong-sig", secret) shouldBe false
                }
            }
            `when`("빈 서명으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, "", secret) shouldBe false
                }
            }
            `when`("다른 시크릿으로 검증") {
                then("false 반환") {
                    verifier.verify(payload, sign(payload, secret), "other-secret") shouldBe false
                }
            }
        }
    })
