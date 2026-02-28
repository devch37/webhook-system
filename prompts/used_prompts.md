# #1 사용한 AI 프롬프트 기록 및 프로젝트 설계
> Layered Architecture + 분산 환경 및 API 보안 고려 설계
> PDF 파일 내용 분석 후 어떤 서브 agent 를 사용하면 좋을 지 추천해줘 ! 보안도 신경 써서 PDF 파일 내용을 상세히 잘 분석해 줘 !
> 각 서브 agent 들을 어떤 플로우로 실행하면 좋을 지도 추천해 줘 !
---

## #2 DB 레이어 구현
**프롬프트:**
```
agents/db-agent.md 파일을 읽고 지시대로 구현해줘.
build.gradle.kts 전체, schema.sql, DatabaseConfig.kt,
domain 모델 (WebhookEvent, Account, Enums, WebhookRequest),
WebhookEventRepository.kt, AccountRepository.kt 생성.
완료 후 prompts/used_prompts.md 에 #2번으로 기록해줘.
```

**구현 내용:**
- `build.gradle.kts` — Spring Boot 3.5.0, Kotlin 2.1.0, Java 25, Kotest 5.9.1, sqlite-jdbc 3.47.1.0, Jacoco 설정
- `src/main/resources/schema.sql` — accounts, webhook_events 테이블 DDL (WAL, FOREIGN KEYS, 인덱스)
- `src/main/resources/application.yaml` — datasource, webhook.secret, server.port 설정
- `src/main/kotlin/com/aladin/webhook/config/DatabaseConfig.kt` — SQLiteDataSource 빈 설정
- `src/main/kotlin/com/aladin/webhook/domain/WebhookEvent.kt` — WebhookEvent data class
- `src/main/kotlin/com/aladin/webhook/domain/Account.kt` — Account data class
- `src/main/kotlin/com/aladin/webhook/domain/Enums.kt` — EventType, EventStatus, AccountStatus enum
- `src/main/kotlin/com/aladin/webhook/domain/dto/WebhookRequest.kt` — WebhookRequest DTO
- `src/main/kotlin/com/aladin/webhook/repository/WebhookEventRepository.kt` — INSERT OR IGNORE, findByEventId, updateStatus, updateFailed
- `src/main/kotlin/com/aladin/webhook/repository/AccountRepository.kt` — findByAccountKey, upsert, updateEmail, updateStatus
- 패키지 `be.com.webhook` → `com.aladin.webhook` 마이그레이션

---

## #3 Business 레이어 구현

**프롬프트:**
```
agents/business-agent.md 파일을 읽고 지시대로 구현해줘.
util/HmacVerifier.kt (타이밍 공격 방지 주석 포함),
util/IdempotencyLockManager.kt (키 잠금, Redis 확장 주석 포함),
service/WebhookService.kt, service/AccountService.kt,
domain/dto/WebhookResult.kt (sealed class) 생성.
HTTP 코드 0줄.
완료 후 prompts/used_prompts.md 에 #3번으로 기록해줘.
```

**구현 내용:**
- `src/main/kotlin/com/aladin/webhook/domain/exception/SignatureVerificationException.kt` — HMAC 검증 실패 예외
- `src/main/kotlin/com/aladin/webhook/domain/exception/NotFoundException.kt` — 리소스 미존재 예외
- `src/main/kotlin/com/aladin/webhook/domain/dto/WebhookResult.kt` — sealed class (Accepted, AlreadyProcessed, Processing)
- `src/main/kotlin/com/aladin/webhook/util/HmacVerifier.kt` — HMAC-SHA256 검증, MessageDigest.isEqual() 타이밍 공격 방지
- `src/main/kotlin/com/aladin/webhook/util/IdempotencyLockManager.kt` — ConcurrentHashMap + ReentrantLock, Redis Redisson 확장 주석
- `src/main/kotlin/com/aladin/webhook/service/AccountService.kt` — EMAIL_FORWARDING_CHANGED / ACCOUNT_DELETED / APPLE_ACCOUNT_DELETED 이벤트 처리
- `src/main/kotlin/com/aladin/webhook/service/WebhookService.kt` — @PostConstruct 시크릿 검증, HMAC 검증, 키 잠금, Idempotency, RECEIVED→PROCESSING→DONE|FAILED 상태 전이

---

## #4 API 레이어 구현

**프롬프트:**
```
agents/api-agent.md 파일을 읽고 지시대로 구현해줘.
controller/WebhookController.kt, controller/AccountController.kt,
controller/InboxController.kt, controller/advice/GlobalExceptionHandler.kt,
domain/exception/Exceptions.kt 생성.
Controller에 비즈니스 로직 0줄.
완료 후 prompts/used_prompts.md 에 #4번으로 기록해줘.
```

**구현 내용:**
- `src/main/kotlin/com/aladin/webhook/controller/WebhookController.kt` — POST /webhooks/account-changes, WebhookService.handle 위임
- `src/main/kotlin/com/aladin/webhook/controller/AccountController.kt` — GET /accounts/{accountKey}, AccountService.findAccount 위임, AccountResponse DTO + toResponse() 확장함수
- `src/main/kotlin/com/aladin/webhook/controller/InboxController.kt` — GET /inbox/events/{eventId}, WebhookService.findEvent 위임, EventResponse DTO + toResponse() 확장함수
- `src/main/kotlin/com/aladin/webhook/controller/advice/GlobalExceptionHandler.kt` — @RestControllerAdvice, SignatureVerificationException(401) / NotFoundException(404) / IllegalArgumentException(400) / MissingRequestHeaderException(400) / Exception(500) 통일 처리
- `src/main/kotlin/com/aladin/webhook/service/AccountService.kt` — findAccount() 조회 메서드 추가 (CLAUDE.md 아키텍처 원칙: Controller→Service→Repository 단방향)
- `src/main/kotlin/com/aladin/webhook/service/WebhookService.kt` — findEvent() 조회 메서드 추가
- `domain/exception/Exceptions.kt` — SignatureVerificationException.kt, NotFoundException.kt 이미 개별 파일로 존재하여 중복 생성 생략

---

## #5 테스트 레이어 구현

**프롬프트:**
```
agents/test-agent.md 파일을 읽고 지시대로 구현해줘.
ProjectConfig.kt, BaseIntegrationSpec.kt,
WebhookControllerSpec.kt (Race Condition 동시 요청 검증 포함),
HmacVerifierSpec.kt (Spring 없이 순수 단위 테스트),
application-test.yml 생성.
완료 후 prompts/used_prompts.md 에 #5번으로 기록해줘.
```

**구현 내용:**
- `src/test/resources/application-test.yml` — SQLite 인메모리 DB, test-secret-key, port: 0
- `src/test/kotlin/com/aladin/webhook/ProjectConfig.kt` — `AbstractProjectConfig`에 Kotest `SpringExtension` 전역 등록
- `src/test/kotlin/com/aladin/webhook/BaseIntegrationSpec.kt` — `@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("test")` 추상 베이스. 람다 수신자를 `BaseIntegrationSpec`으로 지정해 `sign()`, `webhookBody()`, `postWebhook()`, `restTemplate`, `port` 하위 클래스에서 직접 참조 가능
- `src/test/kotlin/com/aladin/webhook/controller/WebhookControllerSpec.kt` — 서명 검증(200/401/400), Idempotency 재전송, Race Condition 5개 동시 요청 → DONE 1개 검증, 이벤트별 처리(EMAIL/ACCOUNT/APPLE), FAILED 기록, 조회 404 포함 통합 테스트
- `src/test/kotlin/com/aladin/webhook/util/HmacVerifierSpec.kt` — Spring 없이 `HmacVerifier()` 직접 인스턴스화, BehaviorSpec 스타일 순수 단위 테스트 (올바른 서명/잘못된 서명/빈 서명/다른 시크릿)
- `src/test/kotlin/com/aladin/webhook/WebhookApplicationTests.kt` — `@ActiveProfiles("test")` 추가
- `src/test/kotlin/be/com/webhook/WebhookApplicationTests.kt` — 구 패키지 테스트에 `classes = [WebhookApplication::class]` 명시 + `@ActiveProfiles("test")` 추가

**커버리지 결과:**
- LINE: 94.6% (157/166)
- INSTRUCTION: 88.0% (1046/1188)
- METHOD: 96.4% (54/56)
- 80% 커버리지 검증 통과 (`jacocoTestCoverageVerification` BUILD SUCCESSFUL)

---

## #6 빌드 최종 확인

**프롬프트:**
```
./gradlew build 실행해서 성공 확인. 에러 있으면 수정해줘.
완료 후 prompts/used_prompts.md 에 #6번으로 기록해줘.
```

**결과:**
- `./gradlew build` BUILD SUCCESSFUL (3s)
- 9개 태스크 모두 UP-TO-DATE (이전 빌드 캐시 활용)
- 컴파일, 테스트, Jacoco 커버리지 검증 전부 통과
- 수정 사항 없음 — 기존 코드 그대로 빌드 성공

---

## #7 테스트 커버리지 80% 달성

**프롬프트:**
```
./gradlew test jacocoTestReport 실행해줘.
커버리지 80% 미만이면 부족한 부분 테스트 추가해서 달성해줘.
완료 후 prompts/used_prompts.md 에 #7번으로 기록해줘.
```

**이전 커버리지 (테스트 추가 전):**
- INSTRUCTION: 88.0% (1046/1188) ✓
- BRANCH: 66.7% (34/51) ✗
- LINE: 94.6% (157/166)

**커버리지 미달 원인 분석:**
- `GlobalExceptionHandler` (BRANCH 2/6): handleBadRequest·handleGeneral 미테스트
- `WebhookService` (BRANCH 13/22): PROCESSING/FAILED/else(`when`) 분기 + validateConfig 실패 분기 미테스트
- `IdempotencyLockManager` (BRANCH 2/4): lock timeout·isHeldByCurrentThread false 분기 미테스트

**추가된 테스트:**
1. `WebhookControllerSpec.kt` — 2개 테스트 추가:
   - `"유효하지 않은 JSON 바디 → 400"` → `GlobalExceptionHandler.handleBadRequest` + `WebhookService.parseRequest` 실패 분기 커버
   - `"이미 실패한 이벤트 재전송 → 이미 처리됨 (실패)"` → `when(existing?.status)` FAILED 분기 커버
2. `service/WebhookServiceSpec.kt` (신규) — Spring 통합 테스트:
   - `"PROCESSING 상태 이벤트 재전송"` → `when(existing?.status)` PROCESSING 분기 커버
   - `"RECEIVED 상태 이벤트 재전송 (else 분기)"` → `when(existing?.status)` else 분기 커버
   - `"빈 시크릿 → IllegalStateException"` → `validateConfig()` 실패 분기 커버
3. `util/IdempotencyLockManagerSpec.kt` (신규) — 순수 단위 테스트:
   - `"정상 실행 → 결과 반환"` — 기본 경로 확인
   - `"락 획득 타임아웃 → IllegalStateException"` → timeout + isHeldByCurrentThread false 분기 커버

**최종 커버리지 결과:**
- INSTRUCTION: 94.8% (1126/1188) ✓
- BRANCH: 84.3% (43/51) ✓
- LINE: 98.2% (163/166) ✓
- COMPLEXITY: 89.2% (74/83) ✓
- METHOD: 98.2% (55/56) ✓
- `jacocoTestCoverageVerification` BUILD SUCCESSFUL

---

## #8 README.md 작성

**프롬프트:**
```
README.md 작성해줘. 포함 내용:
1. 실행방법 (WEBHOOK_SECRET 환경변수 설정 포함)
2. API 명세 요약 (Request/Response 예시 포함)
3. 아키텍처 설명 (Layered Architecture)
4. 분산 환경 설계 고려사항
   - 현재: SQLite 단일 파일 + JVM 내 키 잠금 (단일 인스턴스)
   - 확장: PostgreSQL + Redis 분산 락 교체 포인트 명시
완료 후 prompts/used_prompts.md 에 #8번으로 기록해줘.
```

**구현 내용:**
- `README.md` 생성
- 실행방법: WEBHOOK_SECRET 환경변수 필수 설정, ./gradlew bootRun/bootJar, 테스트 + Jacoco 커버리지 실행법
- API 명세: POST /webhooks/account-changes (HMAC 서명 생성 예시 포함), GET /accounts/{accountKey}, GET /inbox/events/{eventId} — Request/Response JSON 예시 및 상태값 테이블
- 아키텍처: Layered Architecture ASCII 다이어그램, 이벤트 상태 머신 (RECEIVED→PROCESSING→DONE|FAILED), 보안 설계 (타이밍 공격 방지, Idempotency 이중 방어)
- 분산 환경: 현재 구성 한계(SQLite 단일 파일 + JVM 내 ConcurrentHashMap 락) 명시, 교체 포인트 2곳 — DB(SQLite→PostgreSQL), 락(ReentrantLock→Redis Redisson RLock) 코드 예시 포함, 분산 환경 최종 구성 비교표

---

## #9 보안 검토 및 수정

**프롬프트:**
```
agents/security-agent.md 파일을 읽고 체크리스트 항목을 전부 검토해줘.
현재 구현된 코드를 직접 읽으면서 각 항목의 통과/실패 여부를 판단하고,
문제가 있으면 즉시 수정해줘.
수정 완료 후 보안 검토 결과 요약을 prompts/used_prompts.md 에 #9번으로 기록해줘.
```

**보안 검토 결과 요약:**

| 항목 | 검토 전 | 수정 후 |
|------|--------|--------|
| 1.1 MessageDigest.isEqual() 타이밍 공격 방지 | ✅ | ✅ |
| 1.2 서명 실패 로그 서명값 미노출 | ✅ | ✅ |
| 1.3 WEBHOOK_SECRET 최소 32자 검증 | ❌ isNotBlank()만 | ✅ length >= 32 |
| 1.4 WEBHOOK_SECRET 로그 미노출 | ✅ | ✅ |
| 2.1 X-Event-Id 길이 제한 (≤255) | ❌ | ✅ validateHeaders() 추가 |
| 2.2 X-Event-Id 허용 문자 검증 (^[a-zA-Z0-9\-_]+$) | ❌ | ✅ validateHeaders() 추가 |
| 2.3 accountKey 길이 제한 (≤255) | ❌ | ✅ AccountService.process() 추가 |
| 2.4 email 형식 검증 (EMAIL_FORWARDING_CHANGED) | ❌ | ✅ AccountService.process() 추가 |
| 2.5 payload 크기 제한 1MB | ❌ | ✅ application.yaml multipart 설정 |
| 2.6 error_message take(1000) | ✅ | ✅ |
| 3.1 SQL PreparedStatement (모든 쿼리 ? 파라미터) | ✅ | ✅ |
| 3.2 동적 쿼리 조합 없음 | ✅ | ✅ |
| 4.1 server.error.include-stacktrace: never | ❌ | ✅ application.yaml 추가 |
| 4.2 handleGeneral "Internal server error" 고정 | ✅ | ✅ |
| 4.3 로그 개인정보 마스킹 (권장) | 🟡 | 🟡 (운영환경 구조화 로그 권장) |
| 4.4 Actuator (의존성 없음) | N/A | N/A |
| 5.1 tryLock 3초 타임아웃 | ✅ | ✅ |
| 5.2 ConcurrentHashMap locks.remove() | ✅ | ✅ |
| 5.3 INSERT OR IGNORE + 앱 레벨 이중 방어 | ✅ | ✅ |
| 5.4 @Transactional 범위 | ✅ | ✅ |
| 7 HTTP 보안 헤더 (X-Content-Type-Options 등) | ❌ | ✅ SecurityHeaderConfig.kt 생성 |
| 8.1 .gitignore .env/*.db/data/ | ❌ | ✅ 추가 |
| 8.2 application.yaml 시크릿 하드코딩 없음 | ✅ | ✅ |
| 8.3 WEBHOOK_SECRET 기본값 없음 | ✅ | ✅ |

**수정된 파일:**
- `src/main/kotlin/com/aladin/webhook/service/WebhookService.kt` — validateConfig() `length >= 32` 강화, validateHeaders() 메서드 신규 추가 (eventId 길이·문자셋 검증)
- `src/main/kotlin/com/aladin/webhook/service/AccountService.kt` — accountKey 길이 제한, email 정규식 형식 검증 추가
- `src/main/resources/application.yaml` — server.error(include-stacktrace/message/binding-errors: never), spring.servlet.multipart(1MB), spring.mvc.throw-exception-if-no-handler-found, spring.web.resources.add-mappings 추가
- `.gitignore` — `.env`, `*.db`, `data/` 추가
- `src/main/kotlin/com/aladin/webhook/config/SecurityHeaderConfig.kt` — 신규 생성 (X-Content-Type-Options, X-Frame-Options, Cache-Control: no-store)
- `src/test/resources/application-test.yml` — secret 32자 이상으로 갱신 (`test-webhook-secret-key-at-least-32`)
- `src/test/kotlin/com/aladin/webhook/BaseIntegrationSpec.kt` — secret 필드 동기화
- `src/test/kotlin/com/aladin/webhook/service/WebhookServiceSpec.kt` — secret 필드 동기화

**빌드 결과:**
- `./gradlew test` BUILD SUCCESSFUL
- `./gradlew jacocoTestCoverageVerification` BUILD SUCCESSFUL (80% 이상 유지)

## #10 ./data/webhook.db 파일 확인

**프롬프트:**
```
혹시 ./data/webhook.db 파일이 제대로 생성 되지 않은것 같아서 확인 좀 해줘 ! 어플리케이션이 한번에 구동 될 수 있도록 꼼꼼이 확인해 주었으면 좋겠어 !   
```

## #11 JdbcTemplate ORM JPA 로 변경

**프롬프트:**
```
혹시 지금 db access layer 가 JdbcTemplate 을 되어 있는데 혹시 ORM Jpa 로 변경해 줄 수 있을까 ? JdbcTemplate 생 쿼리 이용하는것 보다는 ORM 을 사용하는게 나을 것 같아서 !
```

## #12 sqlite ON UPDATE 확인 요청

**프롬프트:**
```
혹시 sqlite 는 mysql ON UPDATE 처럼 어떤 특정 컬럼이 업데이트 되었을 때 자동으로 update 되는 기능은 없을까 ?
```

















