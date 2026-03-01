# Account Change Webhook 처리 서버

## 🎯 프로젝트 스펙
| 항목 | 버전/선택 |
|------|----------|
| Java | 21 (Kotlin 2.1.0 JVM 25 미지원 → toolchain 21) |
| Kotlin | 2.1.0 |
| Spring Boot | 3.5.0 |
| DB | SQLite3 (xerial sqlite-jdbc 3.47.1.0) |
| ORM | Spring Data JPA + Hibernate Community Dialects |
| 빌드 | Gradle 8.x (Kotlin DSL) |
| 테스트 | Kotest 5.9.1 + kotest-extensions-spring 1.1.3 |
| 아키텍처 | Layered Architecture (Controller → Service → Repository) |

---

## 📁 프로젝트 구조

```
src/main/kotlin/com/aladin/webhook/
│
├── WebhookApplication.kt
│
├── annotation/                        # AOP 마커 어노테이션
│   └── RequiresWebhookSignature.kt    # HMAC 서명 검증 AOP 트리거
│
├── aspect/                            # AOP
│   └── WebhookSignatureAspect.kt      # @Before: 헤더 검증 + HMAC 검증
│
├── config/
│   ├── AsyncConfig.kt                 # @EnableAsync + webhookExecutor (core=4, max=16, queue=500)
│   ├── DatabaseConfig.kt              # SQLite WAL 설정, data/ 디렉토리 자동 생성
│   └── SecurityHeaderConfig.kt        # HTTP 보안 헤더 (X-Content-Type-Options 등)
│
├── controller/                        # HTTP 진입점
│   ├── WebhookController.kt           # POST /webhooks/account-changes → 202/200
│   ├── AccountController.kt           # GET /accounts/{accountKey}
│   ├── InboxController.kt             # GET /inbox/events/{eventId}
│   └── advice/
│       └── GlobalExceptionHandler.kt  # 401/404/400/500 통일 처리
│
├── service/                           # 비즈니스 로직
│   ├── WebhookService.kt              # 잠금 → Idempotency → 이벤트 발행
│   ├── AccountService.kt              # 이벤트 타입별 계정 처리 (@Transactional)
│   ├── WebhookEventListener.kt        # @EventListener → processAsync() 위임 (브릿지)
│   └── WebhookEventProcessor.kt       # @Async("webhookExecutor") 비동기 처리기
│
├── repository/                        # DB 접근 (Spring Data JPA)
│   ├── WebhookEventRepository.kt      # insertIfNotExists, findByEventId, updateStatus, updateFailed
│   ├── WebhookEventJpaRepository.kt   # JpaRepository 인터페이스
│   ├── AccountRepository.kt           # findByAccountKey, upsert, updateEmail, updateStatus
│   └── AccountJpaRepository.kt        # JpaRepository 인터페이스
│
├── domain/                            # 모델 (Entity, DTO, Enum, Event)
│   ├── WebhookEvent.kt                # @Entity (JPA)
│   ├── Account.kt                     # @Entity (JPA)
│   ├── dto/
│   │   ├── WebhookRequest.kt          # 수신 페이로드 DTO
│   │   └── WebhookResult.kt           # sealed class (Queued / AlreadyProcessed / Processing)
│   ├── enum/
│   │   ├── EventType.kt               # EMAIL_FORWARDING_CHANGED / ACCOUNT_DELETED / APPLE_ACCOUNT_DELETED
│   │   ├── EventStatus.kt             # RECEIVED / PROCESSING / DONE / FAILED
│   │   └── AccountStatus.kt           # ACTIVE / DELETED
│   ├── event/
│   │   └── WebhookReceivedEvent.kt    # Spring ApplicationEvent (eventId, rawBody)
│   └── exception/
│       ├── SignatureVerificationException.kt
│       └── NotFoundException.kt
│
└── util/
    ├── HmacVerifier.kt                # HMAC-SHA256 검증 (MessageDigest.isEqual 타이밍 공격 방지)
    └── IdempotencyLockManager.kt      # ConcurrentHashMap + ReentrantLock (Redis 확장 포인트)

src/main/resources/
├── application.yaml                   # DB, JPA, webhook.secret, 보안 헤더, 1MB 제한 설정
└── schema.sql                         # accounts, webhook_events DDL

src/test/kotlin/com/aladin/webhook/
├── ProjectConfig.kt                   # Kotest SpringExtension 전역 등록 (필수)
├── BaseIntegrationSpec.kt             # @SpringBootTest(RANDOM_PORT) + sign/postWebhook 헬퍼
├── WebhookApplicationTests.kt
├── controller/
│   └── WebhookControllerSpec.kt       # 서명 검증, Idempotency, Race Condition, 비동기 상태 폴링
├── service/
│   └── WebhookServiceSpec.kt          # 중복 상태별 처리, validateConfig 실패 분기
└── util/
    ├── HmacVerifierSpec.kt            # 순수 단위 테스트 (Spring 없이)
    └── IdempotencyLockManagerSpec.kt  # 락 정상/타임아웃 분기
```

> **레이어 원칙**: Controller → Service → Repository 단방향만 허용
> Controller에서 Repository 직접 호출 절대 금지

---

## 📋 구현 완료 체크리스트

### API
- [x] `POST /webhooks/account-changes` → 신규 202 Accepted / 중복 200 OK
- [x] `GET /accounts/{accountKey}`
- [x] `GET /inbox/events/{eventId}`

### 보안 & 안정성
- [x] HMAC-SHA256 검증 (MessageDigest.isEqual - 타이밍 공격 방지)
- [x] AOP 분리: `@RequiresWebhookSignature` + `WebhookSignatureAspect` (@Before)
- [x] Idempotency: DB UNIQUE(INSERT OR IGNORE) + 앱 레벨 키 잠금 이중 방어
- [x] 상태 머신: RECEIVED → PROCESSING → DONE | FAILED
- [x] @PostConstruct로 WEBHOOK_SECRET 32자 미만 시 시작 실패
- [x] X-Event-Id 길이(≤255) 및 허용 문자 검증
- [x] accountKey 길이(≤255), email 형식 검증
- [x] payload 크기 제한 1MB (spring.servlet.multipart)
- [x] HTTP 보안 헤더 (X-Content-Type-Options, X-Frame-Options, Cache-Control)
- [x] 스택트레이스/메시지 미노출 (server.error.include-stacktrace: never)
- [x] 모든 예외 @RestControllerAdvice 통일 처리

### 비동기 처리
- [x] PDF 명세 §2-3: "저장 후 비동기 처리" 방식 채택
- [x] `@Async("webhookExecutor")` + `ThreadPoolTaskExecutor` (core=4, max=16, queue=500)
- [x] Spring ApplicationEvent 기반 느슨한 결합 (Kafka 전환 포인트)
- [x] `WebhookEventListener` (@EventListener) → `WebhookEventProcessor` (@Async) 분리

### 이벤트 처리
- [x] EMAIL_FORWARDING_CHANGED
- [x] ACCOUNT_DELETED
- [x] APPLE_ACCOUNT_DELETED

### 테스트 (Kotest, 커버리지 80%+)
- [x] 서명 검증 성공/실패
- [x] Idempotency (중복 eventId, 동시 요청 Race Condition)
- [x] 이벤트별 처리 후 상태 갱신 (비동기 `eventually()` 폴링)
- [x] 실패 케이스 FAILED + error_message
- [x] INSTRUCTION 94.8% / BRANCH 84.3% / LINE 98.2% 달성

---

## 🔄 비동기 처리 플로우

```
POST /webhooks/account-changes
        ↓
[WebhookController]
        ↓ @RequiresWebhookSignature (AOP @Before)
        ↓   → X-Event-Id 헤더 검증
        ↓   → HMAC-SHA256 서명 검증
        ↓
[WebhookService.handle()]
        ↓  1. ReentrantLock (eventId 키 잠금)
        ↓  2. insertIfNotExists (UNIQUE + 자체 @Transactional → 즉시 커밋)
        ↓  3. publishEvent(WebhookReceivedEvent)
        ↓  → return 202 Accepted "수신됨"
        ↓
[WebhookEventListener] @EventListener (동기, 같은 스레드)
        ↓  → processor.processAsync() 호출 (Spring 프록시 경유 → @Async 동작)
        ↓
[WebhookEventProcessor] @Async("webhookExecutor") (별도 스레드)
        ↓  RECEIVED → PROCESSING → DONE | FAILED
        ↓  AccountService.process() @Transactional
```

---

## ⚙️ 환경변수 & 설정

```yaml
# application.yaml (주요 항목)
spring:
  datasource:
    url: jdbc:sqlite:${DB_PATH:./data/webhook.db}
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate.ddl-auto: none
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  servlet:
    multipart:
      max-request-size: 1MB
webhook:
  secret: ${WEBHOOK_SECRET}    # 필수, 32자 이상
server:
  port: ${PORT:8080}
  error:
    include-stacktrace: never
    include-message: never
```

| 환경변수 | 필수 | 기본값 | 설명 |
|----------|------|--------|------|
| `WEBHOOK_SECRET` | ✅ | — | HMAC-SHA256 시크릿 (32자 이상) |
| `DB_PATH` | ❌ | `./data/webhook.db` | SQLite DB 파일 경로 |
| `PORT` | ❌ | `8080` | 서버 포트 |

---

## ✅ 코드 품질 기준

- **타이밍 공격 방지**: HMAC 비교 시 `MessageDigest.isEqual()` 필수
- **동시성**: eventId 기반 ReentrantLock으로 Race Condition 방어 (Redis RLock 교체 포인트)
- **트랜잭션**: `insertIfNotExists` 자체 `@Transactional` (즉시 커밋), `AccountService.process()` 자체 `@Transactional`
- **비동기 설계**: `@Transactional` 미적용 on `handle()` — SQLite WAL `SQLITE_BUSY_SNAPSHOT` 방지
  > PostgreSQL 전환 시: `handle()` `@Transactional` + `@TransactionalEventListener(AFTER_COMMIT)` 이상적
- **환경변수 검증**: `@PostConstruct`로 시크릿 32자 미만 시 즉시 시작 실패
- **에러 일관성**: `@RestControllerAdvice`로 모든 예외 통일 처리
- **Kotlin 관용**: data class, sealed class, when expression 적극 활용
- **로깅**: 보안 이벤트(서명 실패, 중복) 구조화 로그 기록
