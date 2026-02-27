# Account Change Webhook 처리 서버

## 🎯 프로젝트 스펙
| 항목 | 버전/선택 |
|------|----------|
| Java | 25 |
| Kotlin | 2.1.x |
| Spring Boot | 3.5.x |
| DB | SQLite3 (xerial sqlite-jdbc) |
| 빌드 | Gradle 8.x (Kotlin DSL) |
| 테스트 | Kotest 5.x + kotest-extensions-spring |
| 아키텍처 | Layered Architecture (Controller → Service → Repository) |

---

## 📁 프로젝트 구조

```
src/main/kotlin/com/aladin/webhook/
│
├── controller/                    # HTTP 진입점
│   ├── WebhookController.kt
│   ├── AccountController.kt
│   ├── InboxController.kt
│   └── advice/
│       └── GlobalExceptionHandler.kt
│
├── service/                       # 비즈니스 로직
│   ├── WebhookService.kt
│   └── AccountService.kt
│
├── repository/                    # DB 접근 (JdbcTemplate)
│   ├── WebhookEventRepository.kt
│   └── AccountRepository.kt
│
├── domain/                        # 모델 (Entity, DTO, Enum)
│   ├── WebhookEvent.kt
│   ├── Account.kt
│   ├── EventType.kt
│   ├── EventStatus.kt
│   ├── AccountStatus.kt
│   ├── dto/
│   │   ├── WebhookRequest.kt
│   │   └── WebhookResponse.kt
│   └── exception/
│       ├── SignatureVerificationException.kt
│       └── NotFoundException.kt
│
├── util/
│   └── HmacVerifier.kt            # HMAC-SHA256 검증
│
└── config/
    └── DatabaseConfig.kt

src/main/resources/
├── application.yml
└── schema.sql

src/test/kotlin/com/aladin/webhook/
├── ProjectConfig.kt               # Kotest SpringExtension (필수)
├── controller/
│   ├── WebhookControllerSpec.kt
│   ├── AccountControllerSpec.kt
│   └── InboxControllerSpec.kt
├── service/
│   └── WebhookServiceSpec.kt
└── util/
    └── HmacVerifierSpec.kt
```

> **레이어 원칙**: Controller → Service → Repository 단방향만 허용
> Controller에서 Repository 직접 호출 절대 금지

---

## 📋 필수 구현 체크리스트

### API
- [ ] `POST /webhooks/account-changes`
- [ ] `GET /accounts/{accountKey}`
- [ ] `GET /inbox/events/{eventId}`
- [ ] `POST /inbox/process` (선택)

### 보안 & 안정성
- [ ] HMAC-SHA256 검증 (MessageDigest.isEqual - 타이밍 공격 방지)
- [ ] Idempotency: DB UNIQUE(INSERT OR IGNORE) + 앱 레벨 키 잠금 이중 방어
- [ ] 상태 머신: RECEIVED → PROCESSING → DONE | FAILED
- [ ] @PostConstruct로 WEBHOOK_SECRET 미설정 시 시작 실패
- [ ] 모든 예외 @RestControllerAdvice 통일 처리

### 이벤트 처리
- [ ] EMAIL_FORWARDING_CHANGED
- [ ] ACCOUNT_DELETED
- [ ] APPLE_ACCOUNT_DELETED

### 테스트 (Kotest, 커버리지 80%+)
- [ ] 서명 검증 성공/실패
- [ ] Idempotency (중복 eventId, 동시 요청)
- [ ] 이벤트별 처리 후 상태 갱신
- [ ] 실패 케이스 FAILED + error_message

---

## 🤖 서브 에이전트 실행 순서

```bash
# Step 1: DB 레이어
claude "agents/db-agent.md 읽고 구현. 완료 후 prompts/used_prompts.md #2 기록"

# Step 2: 비즈니스 로직
claude "agents/business-agent.md 읽고 구현. 완료 후 prompts/used_prompts.md #3 기록"

# Step 3: API 레이어
claude "agents/api-agent.md 읽고 구현. 완료 후 prompts/used_prompts.md #4 기록"

# Step 4: 테스트
claude "agents/test-agent.md 읽고 구현. 완료 후 prompts/used_prompts.md #5 기록"

# Step 5: 빌드 + 커버리지
claude "./gradlew test jacocoTestReport 실행하고 80% 미만이면 테스트 추가해줘"

# Step 6: README
claude "README.md 작성. 실행방법, API명세, 분산환경 설계 설명 포함"
```

---

## ⚙️ 환경변수 & 설정

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:sqlite:${DB_PATH:./data/webhook.db}
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
webhook:
  secret: ${WEBHOOK_SECRET}
server:
  port: ${PORT:8080}
```

---

## ✅ 코드 품질 기준

- **타이밍 공격 방지**: HMAC 비교 시 `MessageDigest.isEqual()` 필수
- **동시성**: eventId 기반 키 잠금으로 Race Condition 방어
- **트랜잭션**: `@Transactional`로 Idempotency 원자성 보장
- **환경변수 검증**: `@PostConstruct`로 시작 시점에 실패 처리
- **에러 일관성**: `@RestControllerAdvice`로 모든 예외 통일 처리
- **Kotlin 관용**: data class, sealed class, when expression 적극 활용
- **로깅**: 보안 이벤트(서명 실패, 중복) 구조화 로그 기록