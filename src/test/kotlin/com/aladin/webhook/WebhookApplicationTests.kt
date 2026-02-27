package com.aladin.webhook

import io.kotest.core.spec.style.StringSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["webhook.secret=test-webhook-secret-key-at-least-32"])
@ActiveProfiles("test")
class WebhookApplicationTests :
    StringSpec({
        "context loads" {}
    })
