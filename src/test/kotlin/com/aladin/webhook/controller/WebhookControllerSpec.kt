package com.aladin.webhook.controller

import com.aladin.webhook.BaseIntegrationSpec
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class WebhookControllerSpec :
    BaseIntegrationSpec({

        describe("서명 검증") {
            it("올바른 서명 → 202 Accepted") {
                val body = webhookBody("user_001", "ACCOUNT_DELETED")
                postWebhook(body = body).statusCode shouldBe HttpStatus.ACCEPTED
            }

            it("잘못된 서명 → 401") {
                val body = webhookBody("user_002", "ACCOUNT_DELETED")
                postWebhook(body = body, sig = "bad-sig").statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("X-Signature 헤더 누락 → 401") {
                val body = webhookBody("user_003", "ACCOUNT_DELETED")
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Event-Id", UUID.randomUUID().toString())
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("X-Timestamp 헤더 누락 → 401") {
                val body = webhookBody("user_004", "ACCOUNT_DELETED")
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign(body))
                        set("X-Event-Id", UUID.randomUUID().toString())
                        // X-Timestamp 헤더 의도적 누락
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("만료된 타임스탬프 (16분 전) → 401") {
                val expiredTimestamp = (Instant.now().epochSecond - 16 * 60).toString()
                val body = webhookBody("user_005", "ACCOUNT_DELETED")
                postWebhook(body = body, timestamp = expiredTimestamp).statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("미래 타임스탬프 (6분 후) → 401") {
                val futureTimestamp = (Instant.now().epochSecond + 6 * 60).toString()
                val body = webhookBody("user_006", "ACCOUNT_DELETED")
                postWebhook(body = body, timestamp = futureTimestamp).statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("유효 범위 내 타임스탬프 (14분 전) → 202 허용") {
                val recentTimestamp = (Instant.now().epochSecond - 14 * 60).toString()
                val body = webhookBody("user_007", "ACCOUNT_DELETED")
                postWebhook(body = body, timestamp = recentTimestamp).statusCode shouldBe HttpStatus.ACCEPTED
            }

            it("타임스탬프 형식 오류 (숫자 아닌 값) → 401") {
                val body = webhookBody("user_008", "ACCOUNT_DELETED")
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign(body))
                        set("X-Event-Id", UUID.randomUUID().toString())
                        set("X-Timestamp", "not-a-number")
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.UNAUTHORIZED
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

        describe("X-Event-Id 형식 검증") {
            it("255자 초과 eventId → 400") {
                val longEventId = "a".repeat(256)
                val body = webhookBody("user_long_id", "ACCOUNT_DELETED")
                val ts = Instant.now().epochSecond.toString()
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign(body, ts))
                        set("X-Timestamp", ts)
                        set("X-Event-Id", longEventId)
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("허용되지 않은 특수문자 포함 eventId → 400") {
                val body = webhookBody("user_special_id", "ACCOUNT_DELETED")
                val ts = Instant.now().epochSecond.toString()
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign(body, ts))
                        set("X-Timestamp", ts)
                        set("X-Event-Id", "invalid@event#id!")
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("공백만 포함한 eventId → 400") {
                val body = webhookBody("user_blank_id", "ACCOUNT_DELETED")
                val ts = Instant.now().epochSecond.toString()
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign(body, ts))
                        set("X-Timestamp", ts)
                        set("X-Event-Id", "   ")
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.BAD_REQUEST
            }

            it("255자 정확히 eventId → 202 허용") {
                val exactEventId = "a".repeat(255)
                val body = webhookBody("user_exact_id", "ACCOUNT_DELETED")
                val ts = Instant.now().epochSecond.toString()
                val headers =
                    HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Signature", sign(body, ts))
                        set("X-Timestamp", ts)
                        set("X-Event-Id", exactEventId)
                    }
                val res =
                    restTemplate.exchange(
                        "http://localhost:$port/webhooks/account-changes",
                        HttpMethod.POST,
                        HttpEntity(body, headers),
                        Map::class.java,
                    )
                res.statusCode shouldBe HttpStatus.ACCEPTED
            }
        }

        describe("Idempotency") {
            it("동일 eventId 재전송 → 이미 처리됨") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_idem_01", "ACCOUNT_DELETED")

                postWebhook(eventId = eventId, body = body)

                // 비동기 처리 완료(DONE) 대기 후 중복 전송
                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/inbox/events/$eventId", Map::class.java)
                        .body
                        ?.get("status") shouldBe "DONE"
                }

                val res = postWebhook(eventId = eventId, body = body)
                res.statusCode shouldBe HttpStatus.OK
                res.body?.get("message") shouldBe "이미 처리됨"
            }

            it("이미 실패한 이벤트 재전송 → 이미 처리됨 (실패)") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_fail_dup_02", "EMAIL_FORWARDING_CHANGED") // email 없음 → FAILED
                postWebhook(eventId = eventId, body = body)

                // 비동기 처리 완료(FAILED) 대기 후 중복 전송
                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/inbox/events/$eventId", Map::class.java)
                        .body
                        ?.get("status") shouldBe "FAILED"
                }

                val res = postWebhook(eventId = eventId, body = body)
                res.statusCode shouldBe HttpStatus.OK
                res.body?.get("message") shouldBe "이미 처리됨 (실패)"
            }

            it("동시 요청도 한 번만 처리됨 (Race Condition 검증)") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_race_01", "ACCOUNT_DELETED")

                // 5개 동시 요청: 첫 수신은 202, 나머지 중복은 200
                val results =
                    (1..5)
                        .map { CompletableFuture.supplyAsync { postWebhook(eventId = eventId, body = body) } }
                        .map { it.get() }

                results.all { it.statusCode.is2xxSuccessful } shouldBe true

                // 비동기 처리 완료 대기 후 상태 검증
                eventually(5.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/inbox/events/$eventId", Map::class.java)
                        .body
                        ?.get("status") shouldBe "DONE"
                }
            }
        }

        describe("이벤트 처리") {
            it("EMAIL_FORWARDING_CHANGED → 이메일 갱신") {
                val eventId = UUID.randomUUID().toString()
                val body =
                    webhookBody(
                        "user_email_01",
                        "EMAIL_FORWARDING_CHANGED",
                        mapOf("email" to "updated@test.com"),
                    )
                postWebhook(eventId = eventId, body = body).statusCode shouldBe HttpStatus.ACCEPTED

                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/accounts/user_email_01", Map::class.java)
                        .body
                        ?.get("email") shouldBe "updated@test.com"
                }
            }

            it("ACCOUNT_DELETED → status DELETED") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_del_01", "ACCOUNT_DELETED")
                postWebhook(eventId = eventId, body = body).statusCode shouldBe HttpStatus.ACCEPTED

                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/accounts/user_del_01", Map::class.java)
                        .body
                        ?.get("status") shouldBe "DELETED"
                }
            }

            it("APPLE_ACCOUNT_DELETED → status APPLE_DELETED") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_apple_01", "APPLE_ACCOUNT_DELETED")
                postWebhook(eventId = eventId, body = body).statusCode shouldBe HttpStatus.ACCEPTED

                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/accounts/user_apple_01", Map::class.java)
                        .body
                        ?.get("status") shouldBe "APPLE_DELETED"
                }
            }

            it("EMAIL_FORWARDING_CHANGED에 email 누락 → FAILED 기록") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_fail_01", "EMAIL_FORWARDING_CHANGED") // data 없음
                postWebhook(eventId = eventId, body = body).statusCode shouldBe HttpStatus.ACCEPTED

                eventually(3.seconds) {
                    val res =
                        restTemplate.getForEntity(
                            "http://localhost:$port/inbox/events/$eventId",
                            Map::class.java,
                        )
                    res.body?.get("status") shouldBe "FAILED"
                    res.body?.get("errorMessage") shouldNotBe null
                }
            }

            it("잘못된 이메일 형식 → FAILED 기록") {
                val eventId = UUID.randomUUID().toString()
                val body =
                    webhookBody(
                        "user_bad_email_01",
                        "EMAIL_FORWARDING_CHANGED",
                        mapOf("email" to "not-an-email"),
                    )
                postWebhook(eventId = eventId, body = body).statusCode shouldBe HttpStatus.ACCEPTED

                eventually(3.seconds) {
                    val res =
                        restTemplate.getForEntity(
                            "http://localhost:$port/inbox/events/$eventId",
                            Map::class.java,
                        )
                    res.body?.get("status") shouldBe "FAILED"
                    res.body?.get("errorMessage") shouldNotBe null
                }
            }

            it("accountKey 255자 초과 → FAILED 기록") {
                val eventId = UUID.randomUUID().toString()
                val longKey = "k".repeat(256)
                val body = webhookBody(longKey, "ACCOUNT_DELETED")
                postWebhook(eventId = eventId, body = body).statusCode shouldBe HttpStatus.ACCEPTED

                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/inbox/events/$eventId", Map::class.java)
                        .body
                        ?.get("status") shouldBe "FAILED"
                }
            }

            it("연속 이메일 변경 → 마지막 이메일이 반영됨") {
                val accountKey = "user_email_seq_01"

                val eventId1 = UUID.randomUUID().toString()
                val body1 = webhookBody(accountKey, "EMAIL_FORWARDING_CHANGED", mapOf("email" to "first@test.com"))
                postWebhook(eventId = eventId1, body = body1)

                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/inbox/events/$eventId1", Map::class.java)
                        .body
                        ?.get("status") shouldBe "DONE"
                }

                val eventId2 = UUID.randomUUID().toString()
                val body2 = webhookBody(accountKey, "EMAIL_FORWARDING_CHANGED", mapOf("email" to "second@test.com"))
                postWebhook(eventId = eventId2, body = body2)

                eventually(3.seconds) {
                    restTemplate
                        .getForEntity("http://localhost:$port/accounts/$accountKey", Map::class.java)
                        .body
                        ?.get("email") shouldBe "second@test.com"
                }
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

            it("GET /accounts 응답에 필수 필드 포함 (accountKey, status, createdAt, updatedAt)") {
                val accountKey = "user_fields_01"
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody(accountKey, "ACCOUNT_DELETED")
                postWebhook(eventId = eventId, body = body)

                eventually(3.seconds) {
                    val res =
                        restTemplate.getForEntity(
                            "http://localhost:$port/accounts/$accountKey",
                            Map::class.java,
                        )
                    res.statusCode shouldBe HttpStatus.OK
                    res.body?.get("accountKey") shouldBe accountKey
                    res.body?.get("status") shouldBe "DELETED"
                    res.body?.get("createdAt") shouldNotBe null
                    res.body?.get("updatedAt") shouldNotBe null
                }
            }

            it("GET /inbox/events DONE 상태 응답에 필수 필드 포함") {
                val accountKey = "user_inbox_fields_01"
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody(accountKey, "ACCOUNT_DELETED")
                postWebhook(eventId = eventId, body = body)

                eventually(3.seconds) {
                    val res =
                        restTemplate.getForEntity(
                            "http://localhost:$port/inbox/events/$eventId",
                            Map::class.java,
                        )
                    res.statusCode shouldBe HttpStatus.OK
                    res.body?.get("eventId") shouldBe eventId
                    res.body?.get("eventType") shouldBe "ACCOUNT_DELETED"
                    res.body?.get("status") shouldBe "DONE"
                    res.body?.get("errorMessage") shouldBe null
                }
            }

            it("GET /inbox/events FAILED 상태 응답에 errorMessage 포함") {
                val eventId = UUID.randomUUID().toString()
                val body = webhookBody("user_inbox_fail_01", "EMAIL_FORWARDING_CHANGED")
                postWebhook(eventId = eventId, body = body)

                eventually(3.seconds) {
                    val res =
                        restTemplate.getForEntity(
                            "http://localhost:$port/inbox/events/$eventId",
                            Map::class.java,
                        )
                    res.statusCode shouldBe HttpStatus.OK
                    res.body?.get("status") shouldBe "FAILED"
                    res.body?.get("errorMessage") shouldNotBe null
                }
            }
        }
    })
