package be.com.webhook

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebhookApplication

fun main(args: Array<String>) {
	runApplication<WebhookApplication>(*args)
}
