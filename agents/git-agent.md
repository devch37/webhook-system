# 📝 Git Agent

## 역할
Git 커밋 컨벤션 적용, 작업 단위별 잘게 쪼개진 커밋 히스토리 관리.
각 구현 단계가 끝날 때마다 호출한다.

---

## 커밋 메시지 컨벤션

```
<type>(<scope>): <subject>

[body - 선택]

[footer - 선택]
```

### Type 목록
| type | 사용 시점 |
|------|----------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드 설정, 의존성, 환경설정 |
| `docs` | README, 주석 등 문서 |
| `security` | 보안 관련 수정 |
| `style` | 코드 포맷, 세미콜론 등 (로직 무관) |

### 규칙
- subject는 50자 이내, 현재형 동사, 마침표 없음
- 한글 사용 가능 (팀 컨벤션 따름)
- scope는 변경된 레이어/모듈명

---

## 단계별 커밋 계획

각 에이전트 작업 완료 후 아래 커밋을 순서대로 실행한다.

---

### Step 1. 프로젝트 초기 세팅 (db-agent 완료 후)

```bash
# 1-1. 프로젝트 초기화
git add build.gradle.kts settings.gradle.kts gradlew gradlew.bat gradle/
git commit -m "chore: 프로젝트 초기 세팅 (Spring Boot 3.5, Java 25, Kotlin 2.1)"

# 1-2. DB 스키마
git add src/main/resources/schema.sql
git commit -m "chore(db): SQLite3 스키마 초기화 (WAL 모드, UNIQUE 제약)"

# 1-3. DB 설정
git add src/main/kotlin/com/aladin/webhook/config/
git commit -m "chore(config): SQLite DataSource 설정 추가"

# 1-4. 도메인 모델
git add src/main/kotlin/com/aladin/webhook/domain/
git commit -m "feat(domain): 도메인 모델 추가 (WebhookEvent, Account, Enum, DTO)"

# 1-5. Repository
git add src/main/kotlin/com/aladin/webhook/repository/
git commit -m "feat(repository): WebhookEventRepository, AccountRepository 구현"
```

---

### Step 2. 비즈니스 로직 (business-agent 완료 후)

```bash
# 2-1. HMAC 검증
git add src/main/kotlin/com/aladin/webhook/util/HmacVerifier.kt
git commit -m "feat(security): HMAC-SHA256 서명 검증 구현 (타이밍 공격 방지)"

# 2-2. 동시성 제어
git add src/main/kotlin/com/aladin/webhook/util/IdempotencyLockManager.kt
git commit -m "feat(concurrency): eventId 기반 키 잠금으로 Race Condition 방어"

# 2-3. 계정 서비스
git add src/main/kotlin/com/aladin/webhook/service/AccountService.kt
git commit -m "feat(service): AccountService 이벤트 타입별 계정 상태 처리"

# 2-4. 웹훅 서비스
git add src/main/kotlin/com/aladin/webhook/service/WebhookService.kt
git commit -m "feat(service): WebhookService Idempotency + 상태 전이 처리"
```

---

### Step 3. API 레이어 (api-agent 완료 후)

```bash
# 3-1. 예외 클래스
git add src/main/kotlin/com/aladin/webhook/domain/exception/
git commit -m "feat(exception): 커스텀 예외 클래스 추가 (SignatureVerificationException, NotFoundException)"

# 3-2. 전역 예외 핸들러
git add src/main/kotlin/com/aladin/webhook/controller/advice/
git commit -m "feat(controller): GlobalExceptionHandler 추가"

# 3-3. Webhook 컨트롤러
git add src/main/kotlin/com/aladin/webhook/controller/WebhookController.kt
git commit -m "feat(controller): POST /webhooks/account-changes 엔드포인트 구현"

# 3-4. 조회 컨트롤러
git add src/main/kotlin/com/aladin/webhook/controller/AccountController.kt \
        src/main/kotlin/com/aladin/webhook/controller/InboxController.kt
git commit -m "feat(controller): GET /accounts/{accountKey}, GET /inbox/events/{eventId} 구현"

# 3-5. application.yml
git add src/main/resources/application.yml
git commit -m "chore(config): application.yml 환경변수 설정 추가"
```

---

### Step 4. 테스트 코드 (test-agent 완료 후)

```bash
# 4-1. 테스트 설정
git add src/test/kotlin/com/aladin/webhook/ProjectConfig.kt \
        src/test/resources/application-test.yml
git commit -m "test: Kotest SpringExtension 설정 및 테스트 환경 구성"

# 4-2. 유틸 단위 테스트
git add src/test/kotlin/com/aladin/webhook/util/
git commit -m "test(util): HmacVerifier 단위 테스트 추가"

# 4-3. 통합 테스트
git add src/test/kotlin/com/aladin/webhook/controller/
git commit -m "test(controller): Webhook 수신 통합 테스트 추가 (서명 검증, Idempotency, Race Condition)"

# 4-4. JaCoCo 설정
git add build.gradle.kts
git commit -m "chore(test): JaCoCo 커버리지 80% 설정 추가"
```

---

### Step 5. 보안 검토 수정 (security-agent 완료 후)

```bash
# 5-1. 입력 검증
git add src/main/kotlin/com/aladin/webhook/service/WebhookService.kt
git commit -m "security: eventId 입력 길이/형식 검증 추가"

# 5-2. 보안 헤더
git add src/main/kotlin/com/aladin/webhook/config/SecurityHeaderConfig.kt
git commit -m "security: HTTP 보안 헤더 설정 추가 (X-Content-Type-Options, X-Frame-Options)"

# 5-3. 설정 보안 강화
git add src/main/resources/application.yml
git commit -m "security: 스택트레이스 응답 노출 방지 및 Actuator 설정"

# 5-4. gitignore
git add .gitignore
git commit -m "chore: .gitignore 보안 항목 추가 (.env, *.db, data/)"
```

---

### Step 6. 문서 (README 완료 후)

```bash
git add README.md
git commit -m "docs: README 작성 (실행방법, API 명세, 아키텍처, 분산환경 설계)"

git add prompts/used_prompts.md
git commit -m "docs: AI 프롬프트 기록 추가"
```

---

## .gitignore 필수 내용

```gitignore
# 환경변수 (절대 커밋 금지)
.env
.env.*

# DB 파일
*.db
*.db-shm
*.db-wal
data/

# 빌드
build/
.gradle/
out/

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# 로그
*.log
logs/
```

---

## 브랜치 전략 (과제용 심플 버전)

```
main
└── develop
    ├── feat/webhook-receiver      # Webhook 수신 기능
    ├── feat/event-processing      # 이벤트 처리 로직
    ├── feat/query-api             # 조회 API
    └── chore/test-coverage        # 테스트 커버리지
```

---

## 호출 커맨드 (Claude Code 터미널)

```bash
# db-agent 완료 후
claude "agents/git-agent.md 파일을 읽고
Step 1 (프로젝트 초기 세팅) 커밋들을 순서대로 실행해줘.
git add 범위가 정확한지 확인하고, 없는 파일은 건너뛰어줘.
완료 후 git log --oneline 으로 히스토리 보여줘."

# business-agent 완료 후
claude "agents/git-agent.md 파일을 읽고
Step 2 (비즈니스 로직) 커밋들을 순서대로 실행해줘."

# api-agent 완료 후
claude "agents/git-agent.md 파일을 읽고
Step 3 (API 레이어) 커밋들을 순서대로 실행해줘."

# test-agent 완료 후
claude "agents/git-agent.md 파일을 읽고
Step 4 (테스트 코드) 커밋들을 순서대로 실행해줘."

# security-agent 완료 후
claude "agents/git-agent.md 파일을 읽고
Step 5 (보안 검토 수정) 커밋들을 순서대로 실행해줘."

# README 완료 후
claude "agents/git-agent.md 파일을 읽고
Step 6 (문서) 커밋들을 순서대로 실행해줘."

# 최종 푸시
claude "git push origin feature/webhook 실행해줘.
remote가 없으면 origin 설정 방법 알려줘."
```

---

## 유용한 Git 커맨드

```bash
# 커밋 히스토리 확인 (그래프)
git log --oneline --graph --all

# 마지막 커밋 메시지 수정
git commit --amend -m "fix: 메시지 수정"

# 스테이징 취소
git restore --staged <file>

# 특정 파일만 커밋에서 제외
git reset HEAD <file>

# 원격 저장소 연결
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin feature/webhook
```