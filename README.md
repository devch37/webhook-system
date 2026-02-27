# Account Change Webhook 처리 서버

외부 시스템에서 발생하는 계정 변경 이벤트를 수신·처리하는 Webhook 서버입니다.
HMAC-SHA256 서명 검증, Idempotency 보장, 상태 머신 기반 이벤트 처리를 제공합니다.

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Kotlin | 2.1.0 |
| Spring Boot | 3.5.0 |
| JVM | 21 |
| DB | SQLite3 (xerial sqlite-jdbc 3.47.1.0) |
| 빌드 | Gradle 8.x (Kotlin DSL) |
| 테스트 | Kotest 5.9.1 + kotest-extensions-spring |

---

## 실행 방법

### 1. 사전 요구 사항

- JDK 21 이상
- `WEBHOOK_SECRET` 환경변수 필수 (미설정 시 서버 시작 실패)

### 2. 환경변수 설정

```bash
# 필수
export WEBHOOK_SECRET=your-secret-key

# 선택 (기본값 사용 가능)
export DB_PATH=./data/webhook.db   # 기본: ./data/webhook.db
export PORT=8080                   # 기본: 8080
```

### 3. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
WEBHOOK_SECRET=your-secret-key ./gradlew bootRun

# 또는 JAR 직접 실행
./gradlew bootJar
WEBHOOK_SECRET=your-secret-key java -jar build/libs/webhook-*.jar
```

### 4. 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 테스트 + 커버리지 리포트 생성 (build/reports/jacoco/test/html/index.html)
./gradlew test jacocoTestReport

# 커버리지 검증 (80% 미달 시 빌드 실패)
./gradlew jacocoTestCoverageVerification
```

> **테스트 커버리지 현황**: INSTRUCTION 94.8% / BRANCH 84.3% / LINE 98.2%

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
| `X-Signature` | Y | HMAC-SHA256 서명 (hex 인코딩) |
| `X-Event-Id` | Y | 이벤트 고유 ID (Idempotency Key) |
| `Content-Type` | Y | `application/json` |

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
| `EMAIL_FORWARDING_CHANGED` | `data.email`로 계정 이메일 갱신 |
| `ACCOUNT_DELETED` | 계정 상태를 `DELETED`로 변경 |
| `APPLE_ACCOUNT_DELETED` | 계정 상태를 `APPLE_DELETED`로 변경 |

**HMAC 서명 생성 예시 (Python)**

```python
import hmac, hashlib

secret = "your-secret-key"
body = '{"accountKey":"user-account-123","eventType":"EMAIL_FORWARDING_CHANGED","data":{"email":"new@example.com"}}'
signature = hmac.new(secret.encode(), body.encode(), hashlib.sha256).hexdigest()
```

**Response (200 OK)**

```json
{
  "message": "처리됨"
}
```

중복 이벤트(같은 `X-Event-Id`)를 재전송하면 `200`으로 응답하되 재처리하지 않습니다.

```json
{
  "message": "이미 처리됨"
}
```

---

### GET /accounts/{accountKey}

계정 정보를 조회합니다.

**Request**

```
GET /accounts/user-account-123
```

**Response (200 OK)**

```json
{
  "accountKey": "user-account-123",
  "email": "new@example.com",
  "status": "ACTIVE",
  "createdAt": "2026-02-27T10:00:00.000Z",
  "updatedAt": "2026-02-27T10:05:00.000Z"
}
```

| `status` 값 | 의미 |
|-------------|------|
| `ACTIVE` | 활성 계정 |
| `DELETED` | 삭제된 계정 |
| `APPLE_DELETED` | Apple 계정 삭제 |

**Response (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "Account not found: user-account-123"
}
```

---

### GET /inbox/events/{eventId}

수신된 웹훅 이벤트의 처리 상태를 조회합니다.

**Request**

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
  "createdAt": "2026-02-27T10:00:00.000Z",
  "updatedAt": "2026-02-27T10:00:01.000Z"
}
```

| `status` 값 | 의미 |
|-------------|------|
| `RECEIVED` | 수신됨, 처리 대기 |
| `PROCESSING` | 처리 중 |
| `DONE` | 처리 완료 |
| `FAILED` | 처리 실패 (`errorMessage`에 원인 기록) |

**Response (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "Event not found: evt-001"
}
```

---

## 아키텍처

### Layered Architecture

```
HTTP 요청
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  Controller Layer                                       │
│  WebhookController / AccountController / InboxController│
│  • HTTP 파라미터 추출 및 응답 변환만 담당                    │
│  • 비즈니스 로직 0줄 원칙                                  │
└──────────────────────┬──────────────────────────────────┘
                       │ (단방향 의존)
                       ▼
┌─────────────────────────────────────────────────────────┐
│  Service Layer                                          │
│  WebhookService / AccountService                        │
│  • HMAC 서명 검증                                        │
│  • Idempotency 체크 및 상태 전이                          │
│  • 이벤트 타입별 계정 처리                                 │
│  • @Transactional 트랜잭션 관리                           │
└──────────────────────┬──────────────────────────────────┘
                       │ (단방향 의존)
                       ▼
┌─────────────────────────────────────────────────────────┐
│  Repository Layer                                       │
│  WebhookEventRepository / AccountRepository             │
│  • JdbcTemplate 기반 SQL 실행                            │
│  • INSERT OR IGNORE (DB 레벨 Idempotency)               │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
                   SQLite3 DB
```

> **원칙**: Controller → Service → Repository 단방향만 허용. Controller에서 Repository 직접 호출 금지.

### 이벤트 처리 상태 머신

```
수신
  │
  ▼
RECEIVED ──→ PROCESSING ──→ DONE
                  │
                  └──────────→ FAILED (errorMessage 기록)
```

### 보안 설계

- **HMAC-SHA256 검증**: `MessageDigest.isEqual()`로 타이밍 공격(Timing Attack) 방지
- **Idempotency 이중 방어**:
  1. DB 레벨: `event_id UNIQUE` + `INSERT OR IGNORE`
  2. 앱 레벨: `eventId` 기반 `ReentrantLock` 키 잠금 → Race Condition 방어
- **@PostConstruct 검증**: `WEBHOOK_SECRET` 미설정 시 서버 시작 즉시 실패
- **GlobalExceptionHandler**: 모든 예외를 `@RestControllerAdvice`에서 일관된 형식으로 처리

---

## 분산 환경 설계 고려사항

### 현재 구성 (단일 인스턴스)

```
인스턴스 1개
├── IdempotencyLockManager (JVM 내 ConcurrentHashMap + ReentrantLock)
└── SQLite3 단일 파일 (WAL 모드, busy_timeout=5000ms)
```

현재 구성은 단일 JVM 프로세스 내에서만 동시성을 보장합니다.
다중 인스턴스를 동시에 운영하면 다음 문제가 발생합니다.

| 문제 | 원인 |
|------|------|
| Race Condition | JVM 내 락이 다른 인스턴스에 공유되지 않음 |
| 쓰기 충돌 | SQLite는 단일 파일 기반, 다중 프로세스 동시 쓰기 미지원 |

---

### 확장 포인트: 분산 환경 전환

#### 1. DB 교체: SQLite → PostgreSQL

```yaml
# application.yaml 변경 예시
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/webhook
    driver-class-name: org.postgresql.Driver
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

- `schema.sql`의 SQLite 전용 문법(`PRAGMA`, `strftime`) → PostgreSQL 표준 SQL로 교체
- `INSERT OR IGNORE` → `INSERT ... ON CONFLICT DO NOTHING`
- `WebhookEventRepository`, `AccountRepository`의 SQL 수정 필요

#### 2. 분산 락 교체: JVM ReentrantLock → Redis Redisson RLock

**교체 위치**: `IdempotencyLockManager.kt`

```kotlin
// 현재 (JVM 내 메모리 락)
class IdempotencyLockManager {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> withLock(eventId: String, block: () -> T): T {
        val lock = locks.computeIfAbsent(eventId) { ReentrantLock() }
        lock.tryLock(3, TimeUnit.SECONDS)
        // ...
    }
}

// 확장 후 (Redis 분산 락)
class IdempotencyLockManager(private val redissonClient: RedissonClient) {

    fun <T> withLock(eventId: String, block: () -> T): T {
        val lock = redissonClient.getLock("idempotency:$eventId")
        return try {
            check(lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                "Distributed lock timeout for eventId: $eventId"
            }
            block()
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }
}
```

필요 의존성 추가:

```kotlin
// build.gradle.kts
implementation("org.redisson:redisson-spring-boot-starter:3.x.x")
```

#### 3. 분산 환경 최종 구성

```
로드 밸런서
    │
    ├── 인스턴스 1 ─┐
    ├── 인스턴스 2 ─┼──→ Redis (분산 락: idempotency:{eventId})
    └── 인스턴스 N ─┘
                    │
                    └──→ PostgreSQL (공유 DB)
```

| 컴포넌트 | 단일 인스턴스 | 분산 환경 |
|---------|------------|---------|
| 락 관리 | `ConcurrentHashMap` + `ReentrantLock` | Redis Redisson `RLock` |
| DB | SQLite3 (파일) | PostgreSQL (서버) |
| Idempotency | DB UNIQUE + JVM 락 | DB UNIQUE + Redis 분산 락 |
| 교체 범위 | — | `IdempotencyLockManager.kt`, `application.yaml`, `schema.sql` |

인터페이스 변경 없이 `IdempotencyLockManager` 내부 구현과 설정 파일만 교체하면 되므로,
Service/Controller 레이어는 수정 없이 분산 환경으로 전환할 수 있습니다.
