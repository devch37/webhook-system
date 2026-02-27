# Claude Code 터미널 실행 가이드

## 📦 사전 준비

```bash
# Spring Initializr로 프로젝트 생성
curl https://start.spring.io/starter.zip \
  -d type=gradle-project-kotlin \
  -d language=kotlin \
  -d bootVersion=3.5.0 \
  -d javaVersion=25 \
  -d groupId=com.aladin \
  -d artifactId=webhook \
  -d dependencies=web,jdbc \
  -o webhook.zip && unzip webhook.zip -d webhook && cd webhook

# 다운받은 파일들을 프로젝트 루트에 배치
# CLAUDE.md, agents/, prompts/ → 프로젝트 루트

# Claude Code 시작
claude
```

---

## 🚀 단계별 실행 커맨드

### Step 0. 프롬프트 기록 초기화
```bash
claude "prompts/used_prompts.md 파일 생성하고 아래 헤더 추가해줘:
# 사용한 AI 프롬프트 기록
> Layered Architecture + 분산 환경 고려 설계"
```

### Step 1. DB 레이어
```bash
claude "agents/db-agent.md 파일을 읽고 지시대로 구현해줘.
build.gradle.kts 전체, schema.sql, DatabaseConfig.kt,
domain 모델 (WebhookEvent, Account, Enums, WebhookRequest),
WebhookEventRepository.kt, AccountRepository.kt 생성.
완료 후 prompts/used_prompts.md 에 #2번으로 기록해줘."
```

### Step 2. Service 레이어
```bash
claude "agents/business-agent.md 파일을 읽고 지시대로 구현해줘.
util/HmacVerifier.kt (타이밍 공격 방지 주석 포함),
util/IdempotencyLockManager.kt (키 잠금, Redis 확장 주석 포함),
service/WebhookService.kt, service/AccountService.kt,
domain/dto/WebhookResult.kt (sealed class) 생성.
HTTP 코드 0줄.
완료 후 prompts/used_prompts.md 에 #3번으로 기록해줘."
```

### Step 3. Controller 레이어
```bash
claude "agents/api-agent.md 파일을 읽고 지시대로 구현해줘.
controller/WebhookController.kt, controller/AccountController.kt,
controller/InboxController.kt, controller/advice/GlobalExceptionHandler.kt,
domain/exception/Exceptions.kt 생성.
Controller에 비즈니스 로직 0줄.
완료 후 prompts/used_prompts.md 에 #4번으로 기록해줘."
```

### Step 4. 테스트 코드
```bash
claude "agents/test-agent.md 파일을 읽고 지시대로 구현해줘.
ProjectConfig.kt, BaseIntegrationSpec.kt,
WebhookControllerSpec.kt (Race Condition 동시 요청 검증 포함),
HmacVerifierSpec.kt (Spring 없이 순수 단위 테스트),
application-test.yml 생성.
완료 후 prompts/used_prompts.md 에 #5번으로 기록해줘."
```

### Step 5. 빌드 확인
```bash
claude "./gradlew build 실행해서 성공 확인. 에러 있으면 수정해줘.
완료 후 prompts/used_prompts.md 에 #6번으로 기록해줘.
"
```

### Step 6. 테스트 + 커버리지
```bash
claude "./gradlew test jacocoTestReport 실행해줘.
커버리지 80% 미만이면 부족한 부분 테스트 추가해서 달성해줘.
완료 후 prompts/used_prompts.md 에 #7번으로 기록해줘."
```

### Step 7. README.md
```bash
claude "README.md 작성해줘. 포함 내용:
1. 실행방법 (WEBHOOK_SECRET 환경변수 설정 포함)
2. API 명세 요약 (Request/Response 예시 포함)
3. 아키텍처 설명 (Layered Architecture)
4. 분산 환경 설계 고려사항
   - 현재: SQLite 단일 파일 + JVM 내 키 잠금 (단일 인스턴스)
   - 확장: PostgreSQL + Redis 분산 락 교체 포인트 명시
완료 후 prompts/used_prompts.md 에 #8번으로 기록해줘."
```

### Step 7-1. 보안 검토 (구현 완료 후 필수)
```bash
claude "agents/security-agent.md 파일을 읽고 체크리스트 항목을 전부 검토해줘.
현재 구현된 코드를 직접 읽으면서 각 항목의 통과/실패 여부를 판단하고,
문제가 있으면 즉시 수정해줘.
수정 완료 후 보안 검토 결과 요약을 prompts/used_prompts.md 에 #9번으로 기록해줘."
```

### Step 8. 최종 점검
```bash
claude "제출 전 최종 점검:
1. ./gradlew build 성공
2. ./gradlew test 전체 통과
3. 커버리지 80% 이상
4. prompts/used_prompts.md 모든 프롬프트 기록 확인
5. README.md 실행방법, API명세, 분산환경 설명 포함 확인
문제 있으면 바로 수정해줘."
```

---

## 🔧 유용한 추가 커맨드

```bash
# 특정 테스트만 실행
claude "./gradlew test --tests '*WebhookControllerSpec*' 실행하고 결과 알려줘."

# 에러 수정
claude "./gradlew build 에러야: [에러 붙여넣기]. 원인 찾아서 수정해줘."

# 커버리지 낮은 부분 파악
claude "jacocoTestReport 실행하고 커버리지 낮은 클래스 Top 5 알려줘.
각각 추가 테스트 방향 제안해줘."

# 레이어 원칙 점검
claude "현재 코드에서 Controller → Repository 직접 호출하는 부분 있으면 찾아서 수정해줘."
```

---

## ⚡ 서버 실행 및 빠른 테스트

```bash
# 서버 실행
WEBHOOK_SECRET=my-secret ./gradlew bootRun

# 서명 생성 (Python)
python3 -c "
import hmac, hashlib, json
secret = 'my-secret'
body = json.dumps({'accountKey':'user_001','eventType':'ACCOUNT_DELETED','data':{}}, separators=(',',':'))
sig = hmac.new(secret.encode(), body.encode(), hashlib.sha256).hexdigest()
print('Body:', body)
print('Sig :', sig)
"

# Webhook 전송
curl -X POST http://localhost:8080/webhooks/account-changes \
  -H "Content-Type: application/json" \
  -H "X-Signature: [서명값]" \
  -H "X-Event-Id: evt-001" \
  -d '{"accountKey":"user_001","eventType":"ACCOUNT_DELETED","data":{}}'

# 계정 조회
curl http://localhost:8080/accounts/user_001

# 이벤트 결과 조회
curl http://localhost:8080/inbox/events/evt-001
```

---

## 📝 단계별 Git 커밋 (각 Step 완료 직후 실행)

```bash
# Step 1 완료 후 (DB 레이어)
claude "agents/git-agent.md 읽고 Step 1 커밋들 순서대로 실행해줘.
없는 파일은 건너뛰고, 완료 후 git log --oneline 보여줘."

# Step 2 완료 후 (Service 레이어)
claude "agents/git-agent.md 읽고 Step 2 커밋들 순서대로 실행해줘."

# Step 3 완료 후 (Controller 레이어)
claude "agents/git-agent.md 읽고 Step 3 커밋들 순서대로 실행해줘."

# Step 4 완료 후 (테스트)
claude "agents/git-agent.md 읽고 Step 4 커밋들 순서대로 실행해줘."

# Step 5 완료 후 (보안 검토)
claude "agents/git-agent.md 읽고 Step 5 커밋들 순서대로 실행해줘."

# Step 6 완료 후 (README)
claude "agents/git-agent.md 읽고 Step 6 커밋들 순서대로 실행해줘."

# 최종 푸시
claude "git push origin feature/webhook 실행해줘."
```