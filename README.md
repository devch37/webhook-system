# Account Change Webhook 처리 서버

외부 시스템(Apple, 파트너, 내부 시스템)에서 전달되는 계정 변경 Webhook 이벤트를 수신·저장·처리하는 서버입니다.
HMAC-SHA256 서명 검증, Idempotency 보장, 상태 머신 기반 비동기 이벤트 처리를 제공합니다.

---

## 기술 스택

| 항목 | 선택 |
|------|------|
| Language | Kotlin 2.1.0 |
| Framework | Spring Boot 3.5.0 |
| JVM | 21 (Corretto) |
| DB | SQLite 3 (xerial sqlite-jdbc 3.47.1.0) |
| ORM | Spring Data JPA + Hibernate Community Dialects |
| Build | Gradle 8.x (Kotlin DSL) |
| Test | Kotest 5.9.1 + kotest-extensions-spring + JaCoCo |

---

## 실행 방법

### 사전 요구사항

- JDK 21 이상
- `WEBHOOK_SECRET` 환경변수 (32자 이상 필수, 미설정 시 서버 시작 즉시 종료)

### 로컬 실행

```bash
WEBHOOK_SECRET=your-secret-key-replace-this-value \
./gradlew bootRun
```

### JAR 빌드 후 실행

```bash
./gradlew bootJar

WEBHOOK_SECRET=your-secret-key-replace-this-value \
java -jar build/libs/webhook-0.0.1-SNAPSHOT.jar
```

### 환경변수 옵션

| 환경변수 | 필수 | 기본값 | 설명 |
|----------|------|--------|------|
| `WEBHOOK_SECRET` | ✅ | — | HMAC-SHA256 서명 검증 시크릿 (32자 이상) |
| `DB_PATH` | ❌ | `./data/webhook.db` | SQLite DB 파일 경로 |
| `PORT` | ❌ | `8080` | 서버 포트 |

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 테스트 + 커버리지 리포트 생성 (build/reports/jacoco/test/html/index.html)
./gradlew test jacocoTestReport

# 커버리지 80% 검증 포함
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

---

## API 명세

### 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 메시지"
}
```

| HTTP 코드 | 상황 |
|-----------|------|
| 400 | 필수 헤더 누락 또는 잘못된 JSON 바디 |
| 401 | HMAC 서명 검증 실패 |
| 404 | 리소스 미존재 |
| 500 | 서버 내부 오류 |

---

### POST /webhooks/account-changes

계정 변경 이벤트를 수신합니다.

**Request Headers**

| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-Signature` | ✅ | HMAC-SHA256(body, WEBHOOK_SECRET) hex 문자열 |
| `X-Event-Id` | ✅ | 이벤트 고유 ID (영문·숫자·`-`·`_`, 255자 이하) |
| `Content-Type` | ✅ | `application/json` |

**Request Body**

```json
{
  "accountKey": "user-account-123",
  "eventType": "EMAIL_FORWARDING_CHANGED",
  "data": {
    "email": "new@example.com"
  }
}
```

| `eventType` 값 | 처리 내용 |
|----------------|-----------|
| `EMAIL_FORWARDING_CHANGED` | `data.email` 값으로 계정 이메일 갱신 |
| `ACCOUNT_DELETED` | 계정 상태를 `DELETED`로 변경 |
| `APPLE_ACCOUNT_DELETED` | 계정 상태를 `APPLE_DELETED`로 변경 |

**Response**

| 상황 | 상태코드 | 응답 바디 |
|------|----------|-----------|
| 신규 이벤트 수신 (비동기 처리 예약) | `202 Accepted` | `{"message": "수신됨"}` |
| 이미 처리 완료된 이벤트 재전송 | `200 OK` | `{"message": "이미 처리됨"}` |
| 이미 실패한 이벤트 재전송 | `200 OK` | `{"message": "이미 처리됨 (실패)"}` |
| 처리 중인 이벤트 재전송 | `200 OK` | `{"message": "처리 중"}` |

> 비동기 처리이므로 `202`가 반환된 이후에도 실제 처리 결과는 `GET /inbox/events/{eventId}`로 확인해야 합니다.

**HMAC 서명 생성 예시**

```python
# Python
import hmac, hashlib

secret = "your-secret-key"
body = '{"accountKey":"user-account-123","eventType":"ACCOUNT_DELETED","data":{}}'
signature = hmac.new(secret.encode(), body.encode(), hashlib.sha256).hexdigest()
```

```kotlin
// Kotlin
val mac = Mac.getInstance("HmacSHA256")
mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
val signature = mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
```

---

### GET /accounts/{accountKey}

계정 정보를 조회합니다.

```
GET /accounts/user-account-123
```

**Response (200 OK)**

```json
{
  "accountKey": "user-account-123",
  "email": "new@example.com",
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00",
  "updatedAt": "2026-01-01T12:00:00"
}
```

| `status` 값 | 의미 |
|-------------|------|
| `ACTIVE` | 정상 계정 |
| `DELETED` | 앱 내 탈퇴 처리됨 |
| `APPLE_DELETED` | Apple 계정 삭제됨 |

계정이 없으면 `404 Not Found`를 반환합니다.

---

### GET /inbox/events/{eventId}

수신된 Webhook 이벤트의 처리 상태를 조회합니다.

```
GET /inbox/events/evt-001
```

**Response (200 OK)**

```json
{
  "eventId": "evt-001",
  "eventType": "EMAIL_FORWARDING_CHANGED",
  "status": "DONE",
  "errorMessage": null,
  "createdAt": "2026-01-01T00:00:00",
  "updatedAt": "2026-01-01T00:00:01"
}
```

| `status` 값 | 의미 |
|-------------|------|
| `RECEIVED` | 수신됨, 처리 대기 |
| `PROCESSING` | 처리 중 |
| `DONE` | 처리 완료 |
| `FAILED` | 처리 실패 (`errorMessage`에 원인 기록) |

이벤트가 없으면 `404 Not Found`를 반환합니다.

---

## 아키텍처 설계

### 레이어 구조

```
Controller → Service → Repository
```

각 레이어는 단방향 의존만 허용합니다. Controller에서 Repository 직접 호출은 금지합니다.

### 비동기 처리 흐름

명세서 2-3 "저장 후 비동기 처리" 방식을 선택했습니다.
HTTP 스레드는 이벤트를 저장한 즉시 `202 Accepted`를 반환하고, 실제 처리는 별도 스레드풀에서 수행합니다.

```
POST /webhooks/account-changes
        │
        ▼ (HTTP 스레드 - tomcat-exec)
WebhookSignatureAspect  ─── 서명 검증 실패 → 401 즉시 반환
        │
        ▼
WebhookService.handle()
    ├─ IdempotencyLockManager.withLock()   ← Race Condition 방어
    ├─ insertIfNotExists()                 ← DB RECEIVED 저장 (즉시 커밋)
    │       중복이면 → 200 OK 즉시 반환
    └─ publishEvent(WebhookReceivedEvent)
        │
        │ 202 Accepted 반환 ←─────────────────────────┐
        │                                             │
        ▼ (Spring ApplicationEventMulticaster, 동기)  │
WebhookEventListener.onWebhookReceived()             │
        │                                            ─┘ HTTP 응답 완료
        ▼ processor.processAsync() → Spring 프록시 → @Async
WebhookEventProcessor  (webhook-async 스레드풀)
    ├─ RECEIVED → PROCESSING
    ├─ accountService.process()   ─── 성공 → DONE
    └─ 예외 catch                 ─── 실패 → FAILED + errorMessage
```

**ApplicationEvent 기반 설계의 이점**

- `WebhookService`는 이벤트 발행만 담당, 처리 방식을 알지 못합니다 (느슨한 결합)
- 향후 Kafka/RabbitMQ로 전환 시 `publishEvent()` 부분만 교체하면 됩니다
- 추가 리스너(감사 로그, 알림 등)를 독립적으로 붙일 수 있습니다

> **`@EventListener` vs `@TransactionalEventListener` 선택 이유**
> `insertIfNotExists`는 자체 `@Transactional`로 즉시 커밋되므로, `publishEvent` 시점에 RECEIVED 행이 이미 DB에 존재합니다.
> SQLite WAL 모드에서 `handle()`에 `@Transactional`을 추가하면 `SQLITE_BUSY_SNAPSHOT`이 발생하므로,
> `@EventListener`로 충분하며 실질적으로 동일한 안전성을 보장합니다.

**비동기 스레드풀 설정** (`AsyncConfig`)

| 항목 | 값 |
|------|-----|
| core pool size | 4 |
| max pool size | 16 |
| queue capacity | 500 |
| thread name prefix | `webhook-async-` |

### 이벤트 상태 머신

```
수신
  │
  ▼
RECEIVED ──→ PROCESSING ──→ DONE
                  │
                  └──────────→ FAILED (errorMessage 기록)
```

처리 실패 시 외부 시스템에는 정상 응답을 반환하여 무한 재전송 루프를 방지합니다.

### 보안 설계

| 항목 | 구현 |
|------|------|
| HMAC 서명 검증 | `MessageDigest.isEqual()`로 타이밍 공격(Timing Attack) 방지 |
| AOP 분리 | `WebhookSignatureAspect`가 `@RequiresWebhookSignature` 어노테이션에 `@Before`로 자동 적용 |
| 시크릿 검증 | `@PostConstruct`로 시작 시점에 `WEBHOOK_SECRET` 32자 미만이면 즉시 종료 |
| 예외 처리 | `GlobalExceptionHandler`(`@RestControllerAdvice`)로 모든 예외를 일관된 형식으로 처리 |

### Idempotency (중복·재전송 처리)

동일 `X-Event-Id`의 중복 처리를 이중 방어로 차단합니다.

| 방어 레이어 | 방법 |
|------------|------|
| DB 레벨 | `event_id UNIQUE` + `INSERT OR IGNORE` — 원자적 처리 |
| 앱 레벨 | `eventId` 기반 `ReentrantLock` — Race Condition 방어 |

---

## 분산 환경 설계 고려사항

현재 구현은 **단일 인스턴스** 환경을 기준으로 설계되어 있습니다.

### 현재 구성과 한계

```
단일 인스턴스
├── IdempotencyLockManager  ← JVM 내 ConcurrentHashMap + ReentrantLock
└── SQLite3                 ← 파일 기반, 단일 writer (WAL 모드)
```

다중 인스턴스 운영 시:

| 문제 | 원인 |
|------|------|
| Race Condition | JVM 내 락이 다른 인스턴스에 공유되지 않음 |
| 쓰기 충돌 | SQLite는 다중 프로세스 동시 쓰기 미지원 |

### 확장 방향

#### 1. DB 교체: SQLite → PostgreSQL / MySQL

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/webhook
    driver-class-name: org.postgresql.Driver
```

- `INSERT OR IGNORE` → `INSERT ... ON CONFLICT DO NOTHING`
- `strftime()` → DB 표준 날짜 함수로 교체

#### 2. 분산 락: JVM ReentrantLock → Redis Redisson RLock

교체 위치: `IdempotencyLockManager.kt` 내부 구현만 변경하면 됩니다.

```kotlin
// 현재 (JVM 내 메모리 락)
val lock = locks.computeIfAbsent(eventId) { ReentrantLock() }

// 확장 후 (Redis 분산 락)
val lock = redissonClient.getLock("idempotency:$eventId")
lock.tryLock(3, 10, TimeUnit.SECONDS)
```

#### 3. 메시지 큐: @Async → Kafka / RabbitMQ

현재 `@Async` 방식은 서버 재시작 시 처리 중인 이벤트가 유실될 수 있습니다.
`ApplicationEvent` 기반 구조 덕분에 `WebhookService`의 `publishEvent()` 부분만 교체하면 됩니다.

```
현재:  WebhookService → ApplicationEvent → @Async 스레드풀
확장:  WebhookService → Kafka Producer   → Kafka Topic → Consumer
```

비즈니스 로직(`WebhookEventProcessor`)은 변경 없이 그대로 재사용할 수 있습니다.

#### 분산 환경 최종 구성

```
로드 밸런서
    ├── 인스턴스 1 ─┐
    ├── 인스턴스 2 ─┼──→ Redis  (분산 락: idempotency:{eventId})
    └── 인스턴스 N ─┘
                    │
                    └──→ PostgreSQL (공유 DB)
```

| 컴포넌트 | 단일 인스턴스 | 분산 환경 |
|---------|------------|---------|
| 락 관리 | `ReentrantLock` | Redis `RLock` |
| DB | SQLite3 | PostgreSQL |
| 교체 범위 | — | `IdempotencyLockManager.kt`, `application.yaml`, `schema.sql` |

---

## 프로젝트 구조

```
src/main/kotlin/com/aladin/webhook/
├── annotation/
│   └── RequiresWebhookSignature.kt      # 서명 검증 AOP 트리거 어노테이션
├── aspect/
│   └── WebhookSignatureAspect.kt        # HMAC 서명 검증 Aspect (@Before)
├── config/
│   ├── AsyncConfig.kt                   # @EnableAsync + ThreadPoolTaskExecutor
│   └── DatabaseConfig.kt                # SQLite 데이터소스 + WAL 모드 설정
├── controller/
│   ├── WebhookController.kt
│   ├── AccountController.kt
│   ├── InboxController.kt
│   └── advice/
│       └── GlobalExceptionHandler.kt
├── domain/
│   ├── WebhookEvent.kt
│   ├── Account.kt
│   ├── dto/
│   │   ├── WebhookRequest.kt
│   │   └── WebhookResult.kt             # sealed class (Queued / AlreadyProcessed / Processing)
│   ├── enum/
│   │   ├── EventType.kt
│   │   ├── EventStatus.kt
│   │   └── AccountStatus.kt
│   └── event/
│       └── WebhookReceivedEvent.kt      # Spring ApplicationEvent
├── repository/
│   ├── WebhookEventRepository.kt
│   ├── WebhookEventJpaRepository.kt
│   ├── AccountRepository.kt
│   └── AccountJpaRepository.kt
├── service/
│   ├── WebhookService.kt                # 수신·Idempotency 처리·이벤트 발행
│   ├── AccountService.kt                # 계정 상태 갱신
│   ├── WebhookEventListener.kt          # @EventListener 브릿지
│   └── WebhookEventProcessor.kt         # @Async 비동기 처리기
└── util/
    ├── HmacVerifier.kt                  # HMAC-SHA256 검증
    └── IdempotencyLockManager.kt        # 키 기반 ReentrantLock

src/main/resources/
├── application.yaml
└── schema.sql

src/test/kotlin/com/aladin/webhook/
├── ProjectConfig.kt                     # Kotest SpringExtension 설정
├── BaseIntegrationSpec.kt               # 공통 통합 테스트 베이스
├── controller/
│   ├── WebhookControllerSpec.kt         # 서명 검증, Idempotency, Race Condition, 이벤트 처리
│   ├── AccountControllerSpec.kt
│   └── InboxControllerSpec.kt
├── service/
│   └── WebhookServiceSpec.kt
└── util/
    ├── HmacVerifierSpec.kt
    └── IdempotencyLockManagerSpec.kt
```
