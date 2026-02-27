# ⚙️ Business Agent

## 역할
Service 레이어 + 보안/동시성 유틸 구현. HTTP 코드 0줄.

---

## util/HmacVerifier.kt

```kotlin
@Component
class HmacVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 타이밍 공격(Timing Attack) 방지
     * - 일반 문자열 == 비교: 첫 불일치 시 즉시 종료 → 응답 시간으로 시크릿 추론 가능
     * - MessageDigest.isEqual(): 항상 전체 길이 비교 → 상수 시간 보장
     */
    fun verify(payload: String, signature: String, secret: String): Boolean =
        runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            val expected = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                signature.toByteArray(Charsets.UTF_8),
            )
        }.onFailure { log.warn("HMAC verification error: ${it.message}") }
         .getOrDefault(false)
}
```

---

## util/IdempotencyLockManager.kt

```kotlin
/**
 * eventId 기반 키 잠금으로 Race Condition 방어
 *
 * [현재] JVM 내 메모리 락 → 단일 인스턴스 환경
 * [확장] Redis Redisson RLock으로 교체 → 다중 인스턴스 환경
 *   val lock = redissonClient.getLock("idempotency:$eventId")
 *   lock.tryLock(3, 10, TimeUnit.SECONDS)
 *
 * 다중 인스턴스 운영 시 SQLite → PostgreSQL/MySQL 교체도 필요
 * (SQLite는 단일 파일 기반으로 다중 인스턴스 동시 쓰기 미지원)
 */
@Component
class IdempotencyLockManager {

    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> withLock(eventId: String, block: () -> T): T {
        val lock = locks.computeIfAbsent(eventId) { ReentrantLock() }
        return try {
            check(lock.tryLock(3, TimeUnit.SECONDS)) {
                "Lock timeout for eventId: $eventId"
            }
            block()
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
            locks.remove(eventId, lock)  // 메모리 누수 방지
        }
    }
}
```

---

## service/WebhookService.kt

```kotlin
@Service
class WebhookService(
    private val hmacVerifier: HmacVerifier,
    private val lockManager: IdempotencyLockManager,
    private val eventRepository: WebhookEventRepository,
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
    @Value("\${webhook.secret}") private val secret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun validateConfig() {
        check(secret.isNotBlank()) { "WEBHOOK_SECRET must not be blank" }
    }

    /**
     * 처리 흐름:
     * 1. HMAC 서명 검증
     * 2. eventId 키 잠금 (Race Condition 방어)
     * 3. Idempotency 체크 (DB UNIQUE + 앱 레벨 이중 방어)
     * 4. RECEIVED → PROCESSING → DONE | FAILED 상태 전이
     */
    @Transactional
    fun handle(signature: String, eventId: String, rawBody: String): WebhookResult {
        if (!hmacVerifier.verify(rawBody, signature, secret)) {
            log.warn("Signature verification failed. eventId={}", eventId)
            throw SignatureVerificationException("Invalid HMAC signature")
        }

        return lockManager.withLock(eventId) {
            processWithIdempotency(eventId, rawBody)
        }
    }

    private fun processWithIdempotency(eventId: String, rawBody: String): WebhookResult {
        val request = parseRequest(rawBody)

        val isNew = eventRepository.insertIfNotExists(eventId, request.eventType, rawBody)
        if (!isNew) {
            val existing = eventRepository.findByEventId(eventId)
            log.info("Duplicate event. eventId={}, status={}", eventId, existing?.status)
            return when (existing?.status) {
                EventStatus.DONE       -> WebhookResult.AlreadyProcessed("이미 처리됨")
                EventStatus.PROCESSING -> WebhookResult.Processing("처리 중")
                EventStatus.FAILED     -> WebhookResult.AlreadyProcessed("이미 처리됨 (실패)")
                else                   -> WebhookResult.AlreadyProcessed("이미 처리됨")
            }
        }

        eventRepository.updateStatus(eventId, EventStatus.PROCESSING)

        return try {
            accountService.process(request)
            eventRepository.updateStatus(eventId, EventStatus.DONE)
            log.info("Event done. eventId={}, type={}", eventId, request.eventType)
            WebhookResult.Accepted("처리됨")
        } catch (e: Exception) {
            eventRepository.updateFailed(eventId, e.message ?: "Unknown error")
            log.error("Event failed. eventId={}, error={}", eventId, e.message)
            WebhookResult.Accepted("처리됨") // 외부 재전송 루프 방지
        }
    }

    private fun parseRequest(rawBody: String): WebhookRequest =
        runCatching { objectMapper.readValue(rawBody, WebhookRequest::class.java) }
            .getOrElse { throw IllegalArgumentException("Invalid payload: ${it.message}") }
}

// domain/dto/WebhookResult.kt
sealed class WebhookResult(val message: String) {
    class Accepted(message: String)         : WebhookResult(message)
    class AlreadyProcessed(message: String) : WebhookResult(message)
    class Processing(message: String)       : WebhookResult(message)
}
```

---

## service/AccountService.kt

```kotlin
@Service
class AccountService(private val accountRepository: AccountRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun process(request: WebhookRequest) {
        accountRepository.upsert(request.accountKey)  // 계정 없으면 생성

        when (EventType.from(request.eventType)) {
            EventType.EMAIL_FORWARDING_CHANGED -> {
                val email = request.data["email"] as? String
                    ?: throw IllegalArgumentException("data.email required")
                accountRepository.updateEmail(request.accountKey, email)
                log.info("Email updated. accountKey={}", request.accountKey)
            }
            EventType.ACCOUNT_DELETED -> {
                accountRepository.updateStatus(request.accountKey, AccountStatus.DELETED)
                log.info("Account deleted. accountKey={}", request.accountKey)
            }
            EventType.APPLE_ACCOUNT_DELETED -> {
                accountRepository.updateStatus(request.accountKey, AccountStatus.APPLE_DELETED)
                log.info("Apple account deleted. accountKey={}", request.accountKey)
            }
        }
    }
}
```

---

## 호출 커맨드

```bash
claude "agents/business-agent.md 파일을 읽고 지시대로 구현해줘.
util/HmacVerifier.kt (타이밍 공격 방지 주석 포함),
util/IdempotencyLockManager.kt (키 잠금, Redis 확장 주석 포함),
service/WebhookService.kt, service/AccountService.kt,
domain/dto/WebhookResult.kt (sealed class) 생성.
HTTP 코드 0줄.
완료 후 prompts/used_prompts.md 에 #3번으로 기록해줘."
```