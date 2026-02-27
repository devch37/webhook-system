package com.aladin.webhook.controller

import com.aladin.webhook.BaseIntegrationSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.UUID
import java.util.concurrent.CompletableFuture

class WebhookControllerSpec :
    BaseIntegrationSpec({

        describe("서명 검증") {
            it("올바른 서명 → 200") {
                val body = webhookBody("user_001", "ACCOUNT_DELETED")
                postWebhook(body = body).statusCode shouldBe HttpStatus.OK
            }

            it("잘못된 서명 → 401") {
                val body = webhookBody("user_002", "ACCOUNT_DELETED")
                postWebhook(body = body, sig = "bad-sig").statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("X-Event-Id 헤더 누락 → 400") {
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign("body"))
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity("body", headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        describe("Idempotency") {
            it("동일 eventId 재전송 → 이미 처리됨") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_idem_01", "ACCOUNT_DELETED")

                postWebhook(eventId = eventId, body = body)
                val res = postWebhook(eventId = eventId, body = body)

                res.statusCode shouldBe HttpStatus.OK
                res.body?.get("message") shouldBe "이미 처리됨"
            }

            it("이미 실패한 이벤트 재전송 → 이미 처리됨 (실패)") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_fail_dup_02", "EMAIL_FORWARDING_CHANGED") // email 없음 → FAILED
                postWebhook(eventId = eventId, body = body) // first: FAILED
                val res = postWebhook(eventId = eventId, body = body) // duplicate
                res.statusCode shouldBe HttpStatus.OK
                res.body?.get("message") shouldBe "이미 처리됨 (실패)"
            }

            it("동시 요청도 한 번만 처리됨 (Race Condition 검증)") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_race_01", "ACCOUNT_DELETED")

                // 5개 동시 요청
                val results =
                    (1..5)
                        .map { CompletableFuture.supplyAsync { postWebhook(eventId = eventId, body = body) } }
                        .map { it.get() }

                results.all { it.statusCode == HttpStatus.OK } shouldBe true

                // 이벤트는 DONE 상태 1개만 존재해야 함
                val event =
                    restTemplate.getForEntity(
                        "http://localhost:$port/inbox/events/$eventId",
                        Map::class.java,
                    )
                event.body?.get("status") shouldBe "DONE"
            }
        }

        describe("이벤트 처리") {
            it("EMAIL_FORWARDING_CHANGED → 이메일 갱신") {
                val body =
                    webhookBody(
                        "user_email_01",
                        "EMAIL_FORWARDING_CHANGED",
                        mapOf("email" to "updated@test.com"),
                    )
                postWebhook(body = body).statusCode shouldBe HttpStatus.OK

                val res =
                    restTemplate.getForEntity(
                        "http://localhost:$port/accounts/user_email_01",
                        Map::class.java,
                    )
                res.body?.get("email") shouldBe "updated@test.com"
            }

            it("ACCOUNT_DELETED → status DELETED") {
                val body = webhookBody("user_del_01", "ACCOUNT_DELETED")
                postWebhook(body = body).statusCode shouldBe HttpStatus.OK

                val res =
                    restTemplate.getForEntity(
                        "http://localhost:$port/accounts/user_del_01",
                        Map::class.java,
                    )
                res.body?.get("status") shouldBe "DELETED"
            }

            it("APPLE_ACCOUNT_DELETED → status APPLE_DELETED") {
                val body = webhookBody("user_apple_01", "APPLE_ACCOUNT_DELETED")
                postWebhook(body = body).statusCode shouldBe HttpStatus.OK

                val res =
                    restTemplate.getForEntity(
                        "http://localhost:$port/accounts/user_apple_01",
                        Map::class.java,
                    )
                res.body?.get("status") shouldBe "APPLE_DELETED"
            }

            it("EMAIL_FORWARDING_CHANGED에 email 누락 → FAILED 기록") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_fail_01", "EMAIL_FORWARDING_CHANGED") // data 없음
                postWebhook(eventId = eventId, body = body)

                val res =
                    restTemplate.getForEntity(
                        "http://localhost:$port/inbox/events/$eventId",
                        Map::class.java,
                    )
                res.body?.get("status") shouldBe "FAILED"
                res.body?.get("errorMessage") shouldNotBe null
            }
        }

        describe("잘못된 요청") {
            it("유효하지 않은 JSON 바디 → 400") {
                val body = "invalid-json-body!!"
                postWebhook(body = body).statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        describe("조회 API") {
            it("GET /accounts - 없는 계정 → 404") {
                restTemplate
                    .getForEntity(
                        "http://localhost:$port/accounts/nonexistent",
                        Map::class.java,
                    ).statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("GET /inbox/events - 없는 eventId → 404") {
                restTemplate
                    .getForEntity(
                        "http://localhost:$port/inbox/events/nonexistent",
                        Map::class.java,
                    ).statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }
    })
