# 🧪 Test Agent

## 역할
Kotest 기반 의미 있는 테스트. 단순 커버리지가 아닌 비즈니스 시나리오 검증.

---

## ProjectConfig.kt (필수 - 없으면 Spring DI 동작 안함)

```kotlin
// src/test/kotlin/com/aladin/webhook/ProjectConfig.kt
class ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension)
}
```

---

## application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:sqlite::memory:?cache=shared&mode=memory
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
webhook:
  secret: test-secret-key
server:
  port: 0
```

---

## BaseIntegrationSpec.kt

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseIntegrationSpec(body: DescribeSpec.() -> Unit) : DescribeSpec(body) {

    @Autowired lateinit var restTemplate: TestRestTemplate
    @LocalServerPort var port: Int = 0

    val secret = "test-secret-key"

    fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun webhookBody(
        accountKey: String,
        eventType: String,
        data: Map<String, Any> = emptyMap(),
    ) = ObjectMapper().writeValueAsString(
        mapOf("accountKey" to accountKey, "eventType" to eventType, "data" to data)
    )

    fun postWebhook(
        eventId: String = UUID.randomUUID().toString(),
        body: String,
        sig: String? = null,
    ): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Signature", sig ?: sign(body))
            set("X-Event-Id", eventId)
        }
        return restTemplate.exchange(
            "http://localhost:$port/webhooks/account-changes",
            HttpMethod.POST, HttpEntity(body, headers), Map::class.java
        )
    }
}
```

---

## WebhookControllerSpec.kt (통합 테스트)

```kotlin
class WebhookControllerSpec : BaseIntegrationSpec({

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
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Signature", sign("body"))
            }
            val res = restTemplate.exchange(
                "http://localhost:$port/webhooks/account-changes",
                HttpMethod.POST, HttpEntity("body", headers), Map::class.java
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

        it("동시 요청도 한 번만 처리됨 (Race Condition 검증)") {
            val eventId = UUID.randomUUID().toString()
            val body = webhookBody("user_race_01", "ACCOUNT_DELETED")

            // 5개 동시 요청
            val results = (1..5)
                .map { CompletableFuture.supplyAsync { postWebhook(eventId = eventId, body = body) } }
                .map { it.get() }

            results.all { it.statusCode == HttpStatus.OK } shouldBe true

            // 이벤트는 DONE 상태 1개만 존재해야 함
            val event = restTemplate.getForEntity(
                "http://localhost:$port/inbox/events/$eventId", Map::class.java
            )
            event.body?.get("status") shouldBe "DONE"
        }
    }

    describe("이벤트 처리") {
        it("EMAIL_FORWARDING_CHANGED → 이메일 갱신") {
            val body = webhookBody("user_email_01", "EMAIL_FORWARDING_CHANGED",
                mapOf("email" to "updated@test.com"))
            postWebhook(body = body).statusCode shouldBe HttpStatus.OK

            val res = restTemplate.getForEntity(
                "http://localhost:$port/accounts/user_email_01", Map::class.java)
            res.body?.get("email") shouldBe "updated@test.com"
        }

        it("ACCOUNT_DELETED → status DELETED") {
            val body = webhookBody("user_del_01", "ACCOUNT_DELETED")
            postWebhook(body = body).statusCode shouldBe HttpStatus.OK

            val res = restTemplate.getForEntity(
                "http://localhost:$port/accounts/user_del_01", Map::class.java)
            res.body?.get("status") shouldBe "DELETED"
        }

        it("APPLE_ACCOUNT_DELETED → status APPLE_DELETED") {
            val body = webhookBody("user_apple_01", "APPLE_ACCOUNT_DELETED")
            postWebhook(body = body).statusCode shouldBe HttpStatus.OK

            val res = restTemplate.getForEntity(
                "http://localhost:$port/accounts/user_apple_01", Map::class.java)
            res.body?.get("status") shouldBe "APPLE_DELETED"
        }

        it("EMAIL_FORWARDING_CHANGED에 email 누락 → FAILED 기록") {
            val eventId = UUID.randomUUID().toString()
            val body = webhookBody("user_fail_01", "EMAIL_FORWARDING_CHANGED") // data 없음
            postWebhook(eventId = eventId, body = body)

            val res = restTemplate.getForEntity(
                "http://localhost:$port/inbox/events/$eventId", Map::class.java)
            res.body?.get("status") shouldBe "FAILED"
            res.body?.get("errorMessage") shouldNotBe null
        }
    }

    describe("조회 API") {
        it("GET /accounts - 없는 계정 → 404") {
            restTemplate.getForEntity(
                "http://localhost:$port/accounts/nonexistent", Map::class.java
            ).statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("GET /inbox/events - 없는 eventId → 404") {
            restTemplate.getForEntity(
                "http://localhost:$port/inbox/events/nonexistent", Map::class.java
            ).statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }
})
```

---

## HmacVerifierSpec.kt (단위 테스트 - Spring 없이)

```kotlin
class HmacVerifierSpec : BehaviorSpec({

    val verifier = HmacVerifier()

    fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    given("올바른 시크릿") {
        val payload = """{"accountKey":"user","eventType":"ACCOUNT_DELETED"}"""
        val secret  = "my-secret"

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
```

---

## 호출 커맨드

```bash
claude "agents/test-agent.md 파일을 읽고 지시대로 구현해줘.
ProjectConfig.kt (SpringExtension 필수),
BaseIntegrationSpec.kt,
WebhookControllerSpec.kt (동시 요청 Race Condition 검증 포함),
HmacVerifierSpec.kt (순수 단위 테스트),
application-test.yml (SQLite 인메모리) 생성.
완료 후 prompts/used_prompts.md 에 #5번으로 기록해줘."
```