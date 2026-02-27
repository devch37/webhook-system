# 🔒 Security Agent

## 역할
구현된 코드 전체를 API / 애플리케이션 / 인프라 레벨에서 보안 취약점을 검토하고 수정한다.
구현이 모두 완료된 후 마지막에 실행하는 에이전트.

---

## 체크리스트 (Claude Code가 코드를 읽으며 항목별로 검토)

---

### 1. HMAC 서명 검증

```
[ ] MessageDigest.isEqual() 사용 (타이밍 공격 방지)
    - 일반 == 비교 사용 시 즉시 수정

[ ] 서명 검증 실패 시 로그에 서명값 원문 노출 없음
    - log.warn("failed. sig={}", signature) → 금지
    - log.warn("Signature verification failed. eventId={}") → 올바름

[ ] WEBHOOK_SECRET 최소 길이 검증 (32자 이상 권장)
    check(secret.length >= 32) { "WEBHOOK_SECRET must be at least 32 characters" }

[ ] WEBHOOK_SECRET 로그 노출 없음
    - 환경변수 값이 어떤 로그에도 출력되지 않는지 확인
```

---

### 2. 입력 검증 (Input Validation)

```
[ ] X-Event-Id 길이 제한
    require(eventId.length <= 255) { "eventId too long" }

[ ] X-Event-Id 허용 문자 검증 (영숫자, -, _ 만 허용)
    require(eventId.matches(Regex("^[a-zA-Z0-9\\-_]+$"))) { "Invalid eventId format" }

[ ] accountKey 길이 제한
    require(accountKey.length <= 255) { "accountKey too long" }

[ ] email 형식 검증 (EMAIL_FORWARDING_CHANGED)
    require(email.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) { "Invalid email format" }

[ ] payload 크기 제한 (Spring 기본 2MB → 명시적 설정 권장)
    # application.yml
    spring.servlet.multipart.max-request-size: 1MB
    spring.mvc.throw-exception-if-no-handler-found: true

[ ] error_message DB 저장 시 길이 제한 (현재 take(1000) 확인)
```

---

### 3. SQL Injection 방어

```
[ ] 모든 쿼리 PreparedStatement (JdbcTemplate의 ? 파라미터) 사용
    - jdbc.query("... WHERE id = '$id'") → 절대 금지
    - jdbc.query("... WHERE id = ?", id) → 올바름

[ ] 동적 쿼리 생성 없음 확인
    - 문자열 연산으로 SQL 조합하는 부분 전수 검사
```

---

### 4. 민감 정보 노출 방지

```
[ ] 에러 응답에 스택트레이스 미노출
    # application.yml 확인
    server:
      error:
        include-stacktrace: never
        include-message: never   # 운영 환경
        include-binding-errors: never

[ ] GlobalExceptionHandler의 일반 예외 처리
    - "Internal server error" 고정 문구만 반환 (e.message 직접 노출 금지)

[ ] 로그에 개인정보(email, accountKey) 마스킹 고려
    - 현재 로그: log.info("Email updated. accountKey={}", accountKey)
    - 개선안: 프로덕션에서는 마스킹 또는 구조화 로그 사용

[ ] actuator 엔드포인트 비활성화 또는 보호
    # application.yml
    management:
      endpoints:
        web:
          exposure:
            include: health   # health만 노출
```

---

### 5. 동시성 & Idempotency 보안

```
[ ] IdempotencyLockManager tryLock 타임아웃 설정 확인 (3초)
    - 무한 대기 방지

[ ] ConcurrentHashMap 메모리 누수 방지
    - finally 블록에서 locks.remove(eventId, lock) 호출 확인

[ ] DB UNIQUE 제약 + 앱 레벨 잠금 이중 방어 확인
    - INSERT OR IGNORE 없이 SELECT → INSERT 패턴 사용 시 Race Condition 취약

[ ] @Transactional 범위 확인
    - insertIfNotExists + updateStatus가 같은 트랜잭션 내에 있는지 검토
```

---

### 6. 의존성 보안

```
[ ] 사용 중인 라이브러리 알려진 취약점 스캔
    ./gradlew dependencyCheckAnalyze
    (OWASP Dependency Check 플러그인 추가 필요)

[ ] SQLite JDBC 버전 최신 여부 확인
    - 현재: org.xerial:sqlite-jdbc:3.47.1.0
    - https://github.com/xerial/sqlite-jdbc/releases 확인

[ ] Spring Boot 버전 보안 패치 포함 여부 확인
```

---

### 7. HTTP 보안 헤더

```kotlin
// config/SecurityHeaderConfig.kt 추가 권장
@Configuration
class SecurityHeaderConfig : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(SecurityHeaderInterceptor())
    }
}

class SecurityHeaderInterceptor : HandlerInterceptor {
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("Cache-Control", "no-store")
    }
}
```

---

### 8. 환경변수 & 설정 보안

```
[ ] .env 파일 .gitignore 등록 확인
    echo ".env" >> .gitignore
    echo "*.db" >> .gitignore
    echo "data/" >> .gitignore

[ ] application.yml에 시크릿 하드코딩 없음
    - webhook.secret: "hardcoded-secret" → 절대 금지
    - webhook.secret: ${WEBHOOK_SECRET} → 올바름

[ ] WEBHOOK_SECRET 기본값 없음 확인
    - ${WEBHOOK_SECRET:default-secret} → 금지 (기본값 있으면 미설정 감지 불가)
    - ${WEBHOOK_SECRET} → 올바름
```

---

## 수정 우선순위

| 우선순위 | 항목 | 이유 |
|---------|------|------|
| 🔴 즉시 | HMAC MessageDigest.isEqual 미사용 | 타이밍 공격 직접 노출 |
| 🔴 즉시 | SQL 동적 쿼리 조합 | SQL Injection |
| 🔴 즉시 | 스택트레이스 응답 노출 | 내부 구조 노출 |
| 🟡 권장 | 입력값 길이/형식 검증 | DoS, 예상치 못한 오류 |
| 🟡 권장 | .gitignore 설정 | 시크릿 커밋 사고 |
| 🟢 개선 | HTTP 보안 헤더 | 방어 심층화 |
| 🟢 개선 | 로그 마스킹 | 개인정보 보호 |

---

## 보안 강화 코드 (발견된 문제 수정 시 참고)

### 입력 검증 추가 (WebhookService.kt)
```kotlin
private fun validateHeaders(eventId: String) {
    require(eventId.isNotBlank()) { "eventId must not be blank" }
    require(eventId.length <= 255) { "eventId too long" }
    require(eventId.matches(Regex("^[a-zA-Z0-9\\-_]+\$"))) {
        "eventId contains invalid characters"
    }
}
```

### application.yml 보안 설정 추가
```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
    include-binding-errors: never

spring:
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false

management:
  endpoints:
    web:
      exposure:
        include: health
```

### .gitignore 필수 항목
```
.env
*.db
data/
build/
.gradle/
```

---

## 호출 커맨드 (Claude Code 터미널)

```bash
# 구현 완료 후 보안 검토 실행
claude "agents/security-agent.md 파일을 읽고 체크리스트 항목을 전부 검토해줘.
현재 구현된 코드를 직접 읽으면서 각 항목의 통과/실패 여부를 판단하고,
문제가 있으면 즉시 수정해줘.
수정 완료 후 보안 검토 결과 요약을 prompts/used_prompts.md 에 기록해줘."
```

```bash
# 특정 항목만 집중 검토
claude "agents/security-agent.md 의 입력 검증 섹션만 검토하고
현재 코드에 누락된 검증 로직을 추가해줘."
```

```bash
# 보안 헤더 추가
claude "agents/security-agent.md 의 HTTP 보안 헤더 섹션을 참고해서
SecurityHeaderConfig.kt 를 생성하고 application.yml 보안 설정을 추가해줘."
```