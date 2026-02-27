package com.aladin.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 람다 수신자를 BaseIntegrationSpec으로 지정해 하위 클래스 람다에서
 * restTemplate, port, sign(), webhookBody(), postWebhook() 직접 참조 가능.
 * it { ... } 블록은 등록만 되고 실행은 Spring 주입 완료 후이므로
 * @Autowired / @LocalServerPort 필드는 실행 시점에 정상 참조됨.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseIntegrationSpec(
    body: BaseIntegrationSpec.() -> Unit,
) : DescribeSpec() {
    @Autowired lateinit var restTemplate: TestRestTemplate

    @LocalServerPort var port: Int = 0

    val secret = "test-webhook-secret-key-at-least-32"

    init {
        body() // DescribeSpec DSL 등록: this == BaseIntegrationSpec 인스턴스
    }

    fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac
            .doFinal(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun webhookBody(
        accountKey: String,
        eventType: String,
        data: Map<String, Any> = emptyMap(),
    ) = ObjectMapper().writeValueAsString(
        mapOf("accountKey" to accountKey, "eventType" to eventType, "data" to data),
    )

    fun postWebhook(
        eventId: String = UUID.randomUUID().toString(),
        body: String,
        sig: String? = null,
    ): ResponseEntity<Map<*, *>> {
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Signature", sig ?: sign(body))
                set("X-Event-Id", eventId)
            }
        return restTemplate.exchange(
            "http://localhost:$port/webhooks/account-changes",
            HttpMethod.POST,
            HttpEntity(body, headers),
            Map::class.java,
        )
    }
}
