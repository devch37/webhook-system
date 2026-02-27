# 🌐 API Agent

## 역할
Controller 레이어. Service에 위임만. 비즈니스 로직 0줄.

---

## controller/WebhookController.kt

```kotlin
@RestController
@RequestMapping("/webhooks")
class WebhookController(private val webhookService: WebhookService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/account-changes")
    fun receive(
        @RequestHeader("X-Signature") signature: String,
        @RequestHeader("X-Event-Id")  eventId: String,
        @RequestBody rawBody: String,
    ): ResponseEntity<Map<String, String>> {
        log.info("Webhook received. eventId={}", eventId)
        val result = webhookService.handle(signature, eventId, rawBody)
        return ResponseEntity.ok(mapOf("message" to result.message))
    }
}
```

---

## controller/AccountController.kt

```kotlin
@RestController
@RequestMapping("/accounts")
class AccountController(private val accountRepository: AccountRepository) {

    @GetMapping("/{accountKey}")
    fun getAccount(@PathVariable accountKey: String): ResponseEntity<AccountResponse> {
        val account = accountRepository.findByAccountKey(accountKey)
            ?: throw NotFoundException("Account not found: $accountKey")
        return ResponseEntity.ok(account.toResponse())
    }
}

data class AccountResponse(
    val accountKey: String,
    val email: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

fun Account.toResponse() = AccountResponse(accountKey, email, status.name, createdAt, updatedAt)
```

---

## controller/InboxController.kt

```kotlin
@RestController
@RequestMapping("/inbox")
class InboxController(private val eventRepository: WebhookEventRepository) {

    @GetMapping("/events/{eventId}")
    fun getEvent(@PathVariable eventId: String): ResponseEntity<EventResponse> {
        val event = eventRepository.findByEventId(eventId)
            ?: throw NotFoundException("Event not found: $eventId")
        return ResponseEntity.ok(event.toResponse())
    }
}

data class EventResponse(
    val eventId: String,
    val eventType: String,
    val status: String,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
)

fun WebhookEvent.toResponse() = EventResponse(eventId, eventType, status.name, errorMessage, createdAt, updatedAt)
```

---

## controller/advice/GlobalExceptionHandler.kt

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(SignatureVerificationException::class)
    fun handleUnauthorized(e: SignatureVerificationException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("code" to "UNAUTHORIZED", "message" to (e.message ?: "Unauthorized")))

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("code" to "NOT_FOUND", "message" to (e.message ?: "Not found")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "BAD_REQUEST", "message" to (e.message ?: "Bad request")))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("code" to "MISSING_HEADER", "message" to "Required header: ${e.headerName}"))

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<Map<String, String>> {
        log.error("Unexpected error", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("code" to "INTERNAL_ERROR", "message" to "Internal server error"))
    }
}
```

---

## domain/exception/Exceptions.kt

```kotlin
class SignatureVerificationException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
```

---

## 호출 커맨드

```bash
claude "agents/api-agent.md 파일을 읽고 지시대로 구현해줘.
controller/WebhookController.kt, controller/AccountController.kt,
controller/InboxController.kt, controller/advice/GlobalExceptionHandler.kt,
domain/exception/Exceptions.kt 생성.
Controller에 비즈니스 로직 0줄.
완료 후 prompts/used_prompts.md 에 #4번으로 기록해줘."
```