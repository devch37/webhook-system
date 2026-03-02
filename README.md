# Account Change Webhook 처리 서버

외부 시스템(Apple, 파트너사 등)에서 전달되는 계정 변경 이벤트를 **수신 → 저장 → 비동기 처리**하는 서버입니다.

---

## 목차

1. [빠른 시작](#1-빠른-시작)
2. [API 명세](#2-api-명세)
3. [서명 생성 가이드](#3-서명-생성-가이드)
4. [처리 플로우](#4-처리-플로우)
5. [ERD](#5-erd)
6. [아키텍처](#6-아키텍처)
7. [보안 설계](#7-보안-설계)
8. [기술 스택](#8-기술-스택)
9. [프로젝트 구조](#9-프로젝트-구조)
10. [테스트](#10-테스트)

---

## 1. 빠른 시작

### 사전 요구사항

- **JDK 21** 이상
- `WEBHOOK_SECRET` 환경변수 — **32자 이상** 필수 (미설정 시 서버 시작 즉시 종료)

### 로컬 실행

```bash
WEBHOOK_SECRET=your-secret-key-replace-this-32chars \
./gradlew bootRun
```

### JAR 빌드 후 실행

```bash
./gradlew bootJar

WEBHOOK_SECRET=your-secret-key-replace-this-32chars \
java -jar build/libs/webhook-0.0.1-SNAPSHOT.jar
```

### 환경변수

| 변수명 | 필수 | 기본값 | 설명 |
|--------|------|--------|------|
| `WEBHOOK_SECRET` | ✅ | — | HMAC-SHA256 서명 시크릿 (32자 이상) |
| `DB_PATH` | ❌ | `./data/webhook.db` | SQLite DB 파일 경로 |
| `PORT` | ❌ | `8080` | 서버 포트 |

> DB 파일이 없으면 `data/` 디렉토리와 DB 파일을 자동으로 생성합니다.

---

## 2. API 명세

### 공통 오류 응답

```json
{ "code": "ERROR_CODE", "message": "설명" }
```

| 상태 코드 | 코드 | 발생 상황 |
|-----------|------|-----------|
| `400` | `BAD_REQUEST` | 잘못된 JSON, X-Event-Id 형식 오류 |
| `400` | `MISSING_HEADER` | 필수 헤더 누락 (X-Event-Id) |
| `401` | `UNAUTHORIZED` | 서명 불일치, 타임스탬프 만료·누락 |
| `404` | `NOT_FOUND` | 리소스 미존재 |
| `500` | `INTERNAL_ERROR` | 서버 내부 오류 |

---

### POST `/webhooks/account-changes`

계정 변경 이벤트를 수신합니다.

#### 요청 헤더

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Content-Type` | ✅ | `application/json` |
| `X-Event-Id` | ✅ | 이벤트 고유 ID — 영문·숫자·`-`·`_`, **255자 이하** |
| `X-Signature` | ✅ | HMAC-SHA256 서명 (hex) — [서명 생성 방법](#3-서명-생성-가이드) |
| `X-Timestamp` | ✅ | 요청 시각 (Unix epoch **초** 단위 정수) — **15분 이내** 유효 |

#### 요청 바디

```json
{
  "accountKey": "user-account-123",
  "eventType": "EMAIL_FORWARDING_CHANGED",
  "data": {
    "email": "new@example.com"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `accountKey` | String | ✅ | 계정 식별자 (255자 이하) |
| `eventType` | String | ✅ | 이벤트 유형 (아래 표 참고) |
| `data` | Object | ❌ | 이벤트별 추가 데이터 |

| `eventType` | `data` 필드 | 처리 내용 |
|-------------|-------------|-----------|
| `EMAIL_FORWARDING_CHANGED` | `email` (필수) | 계정 이메일 갱신 |
| `ACCOUNT_DELETED` | — | 계정 상태 → `DELETED` |
| `APPLE_ACCOUNT_DELETED` | — | 계정 상태 → `APPLE_DELETED` |

#### 응답

| 상황 | 상태 코드 | 응답 바디 |
|------|-----------|-----------|
| 신규 이벤트 → 비동기 처리 예약 | `202 Accepted` | `{"message": "수신됨"}` |
| 이미 완료된 이벤트 재전송 | `200 OK` | `{"message": "이미 처리됨"}` |
| 이미 실패한 이벤트 재전송 | `200 OK` | `{"message": "이미 처리됨 (실패)"}` |
| 처리 중인 이벤트 재전송 | `200 OK` | `{"message": "처리 중"}` |

> `202` 응답은 **수신 확인**이며 처리 완료를 의미하지 않습니다.
> 처리 결과는 `GET /inbox/events/{eventId}` 로 확인하세요.

#### curl 예시

서명은 `X-Timestamp`와 요청 바디를 합쳐 생성합니다. `openssl`로 즉석에서 계산할 수 있습니다.

```bash
SECRET="your-secret-key-replace-this-32chars"
BODY='{"accountKey":"user-account-123","eventType":"EMAIL_FORWARDING_CHANGED","data":{"email":"new@example.com"}}'
TS=$(date +%s)
SIG=$(echo -n "${TS}.${BODY}" | openssl dgst -sha256 -hmac "${SECRET}" | awk '{print $2}')

curl -s -X POST http://localhost:8080/webhooks/account-changes \
  -H "Content-Type: application/json" \
  -H "X-Event-Id: evt-001" \
  -H "X-Timestamp: ${TS}" \
  -H "X-Signature: ${SIG}" \
  -d "${BODY}"
```

이벤트 타입별 바디 예시:

```bash
# ACCOUNT_DELETED
BODY='{"accountKey":"user-account-123","eventType":"ACCOUNT_DELETED","data":{}}'

# APPLE_ACCOUNT_DELETED
BODY='{"accountKey":"user-account-123","eventType":"APPLE_ACCOUNT_DELETED","data":{}}'
```

---

### GET `/accounts/{accountKey}`

계정 정보를 조회합니다.

#### 요청

```
GET /accounts/user-account-123
```

#### 응답 (200 OK)

```json
{
  "accountKey": "user-account-123",
  "email": "new@example.com",
  "status": "ACTIVE",
  "createdAt": "2026-01-01 12:00:00.000",
  "updatedAt": "2026-01-01 12:00:01.123"
}
```

| `status` | 의미 |
|----------|------|
| `ACTIVE` | 정상 계정 |
| `DELETED` | 앱 내 탈퇴 처리됨 |
| `APPLE_DELETED` | Apple 계정 삭제됨 |

계정이 없으면 `404 Not Found`를 반환합니다.

#### curl 예시

```bash
curl -s http://localhost:8080/accounts/user-account-123
```

---

### GET `/inbox/events/{eventId}`

Webhook 이벤트의 처리 상태를 조회합니다.

#### 요청

```
GET /inbox/events/evt-001
```

#### 응답 (200 OK)

```json
{
  "eventId": "evt-001",
  "eventType": "EMAIL_FORWARDING_CHANGED",
  "status": "DONE",
  "errorMessage": null,
  "createdAt": "2026-01-01 12:00:00.000",
  "updatedAt": "2026-01-01 12:00:01.123"
}
```

| `status` | 의미 |
|----------|------|
| `RECEIVED` | 수신됨, 처리 대기 중 |
| `PROCESSING` | 처리 중 |
| `DONE` | 처리 완료 |
| `FAILED` | 처리 실패 (`errorMessage` 에 원인 기록) |

이벤트가 없으면 `404 Not Found`를 반환합니다.

#### curl 예시

```bash
curl -s http://localhost:8080/inbox/events/evt-001
```

---

## 3. 서명 생성 가이드

### 서명 규칙 (Stripe 스타일, Replay Attack 방지)

```
서명 대상  : "{X-Timestamp}.{요청 바디}"
서명 알고리즘: HMAC-SHA256(WEBHOOK_SECRET, 서명 대상)
인코딩    : 소문자 hex 문자열
유효 시간  : 요청 시각 기준 ±15분 이내
```

타임스탬프를 서명 대상에 포함하여 **타임스탬프 변조 방지** 및 **Replay Attack 차단**을 동시에 달성합니다.

### 서명 예시

```python
# Python
import hmac, hashlib, time

secret = "your-secret-key-replace-this-32chars"
body   = '{"accountKey":"user-123","eventType":"ACCOUNT_DELETED","data":{}}'
ts     = str(int(time.time()))         # Unix epoch seconds

signed_payload = f"{ts}.{body}"
signature = hmac.new(
    secret.encode("utf-8"),
    signed_payload.encode("utf-8"),
    hashlib.sha256
).hexdigest()

# 요청 헤더
# X-Timestamp: {ts}
# X-Signature: {signature}
```

```kotlin
// Kotlin
val secret    = "your-secret-key-replace-this-32chars"
val body      = """{"accountKey":"user-123","eventType":"ACCOUNT_DELETED","data":{}}"""
val timestamp = Instant.now().epochSecond.toString()

val mac = Mac.getInstance("HmacSHA256")
mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
val signature = mac.doFinal("$timestamp.$body".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
```

```javascript
// JavaScript (Node.js)
const crypto    = require("crypto");
const secret    = "your-secret-key-replace-this-32chars";
const body      = JSON.stringify({ accountKey: "user-123", eventType: "ACCOUNT_DELETED", data: {} });
const timestamp = Math.floor(Date.now() / 1000).toString();

const signature = crypto
  .createHmac("sha256", secret)
  .update(`${timestamp}.${body}`)
  .digest("hex");
```

---

## 4. 처리 플로우

### 전체 요청 처리 흐름

```
클라이언트
    │
    │  POST /webhooks/account-changes
    │  Headers: X-Signature, X-Timestamp, X-Event-Id
    │  Body:    { accountKey, eventType, data }
    ▼
┌─────────────────────────────────────────────────────┐
│              WebhookSignatureAspect                 │
│  1. X-Signature 존재 확인          → 없으면 401     │
│  2. X-Timestamp 존재 확인          → 없으면 401     │
│  3. 타임스탬프 유효 시간 검증 (15분) → 만료 시 401  │
│  4. X-Event-Id 형식 검증           → 오류 시 400   │
│  5. HMAC-SHA256 서명 비교          → 불일치 시 401  │
└───────────────────────┬─────────────────────────────┘
                        │ 검증 통과
                        ▼
┌─────────────────────────────────────────────────────┐
│               WebhookService.handle()               │
│                                                     │
│  IdempotencyLockManager.withLock(eventId)           │
│  ┌──────────────────────────────────────────────┐  │
│  │  insertIfNotExists(eventId)                  │  │
│  │   ├─ 신규 → DB에 RECEIVED 저장 (즉시 커밋)  │  │
│  │   │         publishEvent()                   │  │
│  │   │         → 202 Accepted 반환              │  │
│  │   └─ 중복 → 현재 상태 확인                  │  │
│  │             DONE/FAILED → 200 "이미 처리됨"  │  │
│  │             PROCESSING  → 200 "처리 중"      │  │
│  └──────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────┘
                        │ ApplicationEvent 발행 (동기)
                        ▼
┌─────────────────────────────────────────────────────┐
│  WebhookEventListener → WebhookEventProcessor       │
│                         (@Async, 별도 스레드풀)      │
│                                                     │
│  RECEIVED ──→ PROCESSING ──→ DONE                  │
│                    │                                │
│                    └──────────────→ FAILED          │
│                              (errorMessage 기록)    │
└─────────────────────────────────────────────────────┘
```

### 이벤트 상태 머신

```
  수신
   │
   ▼
RECEIVED
   │  비동기 처리 시작
   ▼
PROCESSING
   │
   ├─ 성공 ──→ DONE
   │
   └─ 실패 ──→ FAILED  (errorMessage 에 원인 기록)
```

### Idempotency (중복 방지) 흐름

```
동일 X-Event-Id 로 재요청
          │
          ▼
   ReentrantLock 획득 (eventId 키)
          │
          ▼
   INSERT OR IGNORE (DB UNIQUE 제약)
          │
   ┌──── 중복 감지 ────┐
   │                  │
   ▼                  ▼
신규 → 202       기존 상태 확인
                  DONE/FAILED → 200 "이미 처리됨"
                  PROCESSING  → 200 "처리 중"
```

---

## 5. ERD

```
┌──────────────────────────────────────────┐
│               accounts                   │
├──────────────┬──────────┬───────────────┤
│ PK  id       │ INTEGER  │ AUTOINCREMENT  │
│ UQ  account_key│ TEXT   │ NOT NULL       │
│     email    │ TEXT     │                │
│     status   │ TEXT     │ ACTIVE (기본)  │
│              │          │ DELETED        │
│              │          │ APPLE_DELETED  │
│     created_at│ TEXT    │ 밀리초 정밀도  │
│     updated_at│ TEXT    │ 밀리초 정밀도  │
└──────────────┴──────────┴───────────────┘

┌──────────────────────────────────────────┐
│             webhook_events               │
├──────────────┬──────────┬───────────────┤
│ PK  id       │ INTEGER  │ AUTOINCREMENT  │
│ UQ  event_id │ TEXT     │ NOT NULL       │
│     event_type│ TEXT    │ NOT NULL       │
│     payload  │ TEXT     │ 원본 JSON 전체  │
│     status   │ TEXT     │ RECEIVED (기본)│
│              │          │ PROCESSING     │
│              │          │ DONE           │
│              │          │ FAILED         │
│     error_message│ TEXT │ FAILED 시 기록 │
│     created_at│ TEXT    │ 밀리초 정밀도  │
│     updated_at│ TEXT    │ 밀리초 정밀도  │
└──────────────┴──────────┴───────────────┘

INDEX: idx_accounts_key        ON accounts(account_key)
INDEX: idx_webhook_events_status ON webhook_events(status)
```

> `accounts` 와 `webhook_events` 는 직접적인 FK 관계 없이 `accountKey` 로 논리적으로 연결됩니다.

---

## 6. 아키텍처

### 레이어 구조

```
┌─────────────────────────────────────────────────────┐
│                   Controller Layer                  │
│  WebhookController  AccountController  InboxController │
│           ↑ AOP: WebhookSignatureAspect             │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                    Service Layer                     │
│  WebhookService  AccountService  WebhookEventProcessor│
│  WebhookEventListener (ApplicationEvent 브릿지)      │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                  Repository Layer                    │
│  WebhookEventRepository   AccountRepository          │
│  WebhookEventJpaRepository  AccountJpaRepository     │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│               SQLite3  (WAL 모드)                    │
└─────────────────────────────────────────────────────┘
```

> **단방향 규칙**: Controller → Service → Repository 방향만 허용합니다.

### 비동기 처리 구조

```
HTTP 스레드 (tomcat-exec)          webhook-async 스레드풀
        │                                  │
        │  handle()                        │
        │   └─ insertIfNotExists()         │
        │   └─ publishEvent()  ────────────┤
        │                                  │  processAsync()
        └─── 202 반환 (즉시)               │   └─ PROCESSING → DONE/FAILED
                                           │
```

**스레드풀 설정** (AsyncConfig)

| 항목 | 값 |
|------|----|
| core pool size | 4 |
| max pool size | 16 |
| queue capacity | 500 |
| thread name prefix | `webhook-async-` |

### 확장 포인트

```
현재 (단일 인스턴스)         확장 후 (분산 환경)
──────────────────           ─────────────────────────
ReentrantLock           →    Redis Redisson RLock
SQLite3                 →    PostgreSQL / MySQL
@Async + AppEvent       →    Kafka / RabbitMQ Producer
```

교체 대상 파일: `IdempotencyLockManager.kt`, `application.yaml`, `schema.sql`

---

## 7. 보안 설계

| 항목 | 구현 방식 |
|------|-----------|
| **HMAC 서명 검증** | `HMAC-SHA256(secret, "$timestamp.$body")` — Stripe 방식 |
| **타이밍 공격 방지** | `MessageDigest.isEqual()` 상수 시간 비교 |
| **Replay Attack 방지** | `X-Timestamp` 포함 서명 + 15분 유효 시간 |
| **서명 검증 분리** | `@RequiresWebhookSignature` + `WebhookSignatureAspect` (AOP `@Before`) |
| **시크릿 검증** | `@PostConstruct` — 32자 미만이면 서버 시작 즉시 종료 |
| **Idempotency** | DB `UNIQUE + INSERT OR IGNORE` (원자적) + `ReentrantLock` (Race Condition 이중 방어) |
| **에러 정보 차단** | `server.error.include-stacktrace: never` — 스택트레이스·메시지 미노출 |
| **HTTP 보안 헤더** | `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control` |
| **요청 크기 제한** | `1MB` (spring.servlet.multipart.max-request-size) |
| **X-Event-Id 검증** | 형식(`^[a-zA-Z0-9\-_]+$`) + 길이(255자 이하) 검증 |

---

## 8. 기술 스택

| 항목 | 선택 |
|------|------|
| Language | Kotlin 2.1.0 |
| Framework | Spring Boot 3.5.0 |
| JVM | 21 (toolchain) |
| DB | SQLite 3 — xerial sqlite-jdbc 3.47.1.0 |
| ORM | Spring Data JPA + Hibernate Community Dialects |
| Build | Gradle 8.x (Kotlin DSL) |
| Test | Kotest 5.9.1 + kotest-extensions-spring 1.1.3 |
| Coverage | JaCoCo (Instruction 92%+, Branch 87%+) |

---

## 9. 프로젝트 구조

```
src/main/kotlin/com/aladin/webhook/
├── annotation/
│   └── RequiresWebhookSignature.kt      # 서명 검증 AOP 트리거 어노테이션
├── aspect/
│   └── WebhookSignatureAspect.kt        # X-Signature/X-Timestamp 검증 (@Before)
├── config/
│   ├── AsyncConfig.kt                   # @EnableAsync + ThreadPoolTaskExecutor
│   ├── DatabaseConfig.kt                # SQLite WAL 설정 + @EnableJpaAuditing
│   └── SecurityHeaderConfig.kt          # HTTP 보안 헤더
├── controller/
│   ├── WebhookController.kt             # POST /webhooks/account-changes
│   ├── AccountController.kt             # GET /accounts/{accountKey}
│   ├── InboxController.kt               # GET /inbox/events/{eventId}
│   └── advice/
│       └── GlobalExceptionHandler.kt    # 전역 예외 처리 (@RestControllerAdvice)
├── domain/
│   ├── Account.kt                       # @Entity — accounts 테이블
│   ├── WebhookEvent.kt                  # @Entity — webhook_events 테이블
│   ├── BaseEntity.kt                    # created_at / updated_at 공통 (@MappedSuperclass)
│   ├── dto/
│   │   ├── WebhookRequest.kt            # 수신 페이로드 DTO
│   │   └── WebhookResult.kt             # sealed class (Queued / AlreadyProcessed / Processing)
│   ├── enum/
│   │   ├── EventType.kt                 # EMAIL_FORWARDING_CHANGED / ACCOUNT_DELETED / APPLE_ACCOUNT_DELETED
│   │   ├── EventStatus.kt               # RECEIVED / PROCESSING / DONE / FAILED
│   │   └── AccountStatus.kt             # ACTIVE / DELETED / APPLE_DELETED
│   ├── event/
│   │   └── WebhookReceivedEvent.kt      # Spring ApplicationEvent
│   └── exception/
│       ├── SignatureVerificationException.kt
│       └── NotFoundException.kt
├── repository/
│   ├── AccountRepository.kt             # findByAccountKey, upsert, updateEmail, updateStatus
│   ├── AccountJpaRepository.kt
│   ├── WebhookEventRepository.kt        # insertIfNotExists, findByEventId, updateStatus, updateFailed
│   └── WebhookEventJpaRepository.kt
├── service/
│   ├── WebhookService.kt                # 수신·Idempotency·이벤트 발행
│   ├── AccountService.kt                # 이벤트 타입별 계정 처리
│   ├── WebhookEventListener.kt          # @EventListener 브릿지
│   └── WebhookEventProcessor.kt         # @Async 비동기 처리기
└── util/
    ├── HmacVerifier.kt                  # HMAC-SHA256 서명 검증
    ├── IdempotencyLockManager.kt        # eventId 기반 ReentrantLock
    └── LocalDateTimeConverter.kt        # LocalDateTime ↔ TEXT 변환 (JPA AttributeConverter)

src/main/resources/
├── application.yaml                     # 서버 설정 (DB, 서명 유효시간, 포트 등)
└── schema.sql                           # DDL (accounts, webhook_events)

src/test/kotlin/com/aladin/webhook/
├── ProjectConfig.kt                     # Kotest SpringExtension 전역 등록
├── BaseIntegrationSpec.kt               # @SpringBootTest 공통 베이스 + sign()/postWebhook() 헬퍼
├── controller/
│   └── WebhookControllerSpec.kt         # 서명·타임스탬프·Idempotency·Race Condition·이벤트 처리
├── service/
│   ├── WebhookServiceSpec.kt            # 중복 상태별 처리 단위 테스트
│   └── AccountServiceSpec.kt            # 이벤트 타입별 계정 처리 단위 테스트
└── util/
    ├── HmacVerifierSpec.kt              # HMAC 검증 순수 단위 테스트
    ├── IdempotencyLockManagerSpec.kt    # 락 정상/타임아웃 단위 테스트
    └── LocalDateTimeConverterSpec.kt    # LocalDateTime 변환기 단위 테스트

requests/
└── api-test.http                        # IntelliJ HTTP Client 전체 API 테스트
```

---

## 10. 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 테스트 + 커버리지 리포트 (build/reports/jacoco/test/html/index.html)
./gradlew test jacocoTestReport

# 전체 품질 검사 (ktlint + 테스트 + 커버리지 80% 검증)
./gradlew check
```

### 커버리지 현황

| 항목 | 달성률 |
|------|--------|
| Instruction | 92%+ |
| Branch | 87%+ |
| Line | 98%+ |

### 주요 테스트 시나리오

| 분류 | 시나리오 |
|------|----------|
| 서명 검증 | 올바른 서명 → 202 / 잘못된 서명 → 401 / 서명 누락 → 401 |
| 타임스탬프 | 누락 → 401 / 만료(16분) → 401 / 미래(6분) → 401 / 유효(14분) → 202 |
| X-Event-Id | 누락 → 400 / 256자 초과 → 400 / 특수문자 → 400 / 공백 → 400 |
| Idempotency | 동일 eventId 재전송 → 200 / FAILED 이벤트 재전송 → 200 |
| Race Condition | 5개 동시 요청 → 모두 2xx, 정확히 1회만 처리 |
| 이벤트 처리 | EMAIL_FORWARDING_CHANGED / ACCOUNT_DELETED / APPLE_ACCOUNT_DELETED |
| 실패 케이스 | email 누락 → FAILED / 잘못된 이메일 형식 → FAILED / 알 수 없는 eventType → FAILED |
| 조회 API | 없는 계정 → 404 / 없는 이벤트 → 404 / 응답 필드 구조 검증 |
