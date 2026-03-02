## #1 사용한 AI 프롬프트 기록 및 프로젝트 설계
```
Layered Architecture + 분산 환경 및 API 보안 고려 설계
PDF 파일 내용 분석 후 어떤 서브 agent 를 사용하면 좋을 지 추천해줘 ! 보안도 신경 써서 PDF 파일 내용을 상세히 잘 분석해 줘 !
 각 서브 agent 들을 어떤 플로우로 실행하면 좋을 지도 추천해 줘 !
```

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

---

## #6 빌드 최종 확인

**프롬프트:**
```
./gradlew build 실행해서 성공 확인. 에러 있으면 수정해줘.
완료 후 prompts/used_prompts.md 에 #6번으로 기록해줘.
```

---

## #7 테스트 커버리지 80% 달성

**프롬프트:**
```
./gradlew test jacocoTestReport 실행해줘.
커버리지 80% 미만이면 부족한 부분 테스트 추가해서 달성해줘.
완료 후 prompts/used_prompts.md 에 #7번으로 기록해줘.
```

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

---

## #9 보안 검토 및 수정

**프롬프트:**
```
agents/security-agent.md 파일을 읽고 체크리스트 항목을 전부 검토해줘.
현재 구현된 코드를 직접 읽으면서 각 항목의 통과/실패 여부를 판단하고,
문제가 있으면 즉시 수정해줘.
수정 완료 후 보안 검토 결과 요약을 prompts/used_prompts.md 에 #9번으로 기록해줘.
```

---

## #10 ./data/webhook.db 파일 확인

**프롬프트:**
```
혹시 ./data/webhook.db 파일이 제대로 생성 되지 않은것 같아서 확인 좀 해줘 ! 어플리케이션이 한번에 구동 될 수 있도록 꼼꼼이 확인해 주었으면 좋겠어 !   
```

---

## #11 JdbcTemplate ORM JPA 로 변경

**프롬프트:**
```
혹시 지금 db access layer 가 JdbcTemplate 을 되어 있는데 혹시 ORM Jpa 로 변경해 줄 수 있을까 ? JdbcTemplate 생 쿼리 이용하는것 보다는 ORM 을 사용하는게 나을 것 같아서 !
```

---

## #12 sqlite ON UPDATE 확인 요청

**프롬프트:**
```
혹시 sqlite 는 mysql ON UPDATE 처럼 어떤 특정 컬럼이 업데이트 되었을 때 자동으로 update 되는 기능은 없을까 ?
```

---

## #13 프로젝트 중간 pdf 명세 확인 요청
**프롬프트**
```
 혹시 코드를 다 구현한거는 아닌데 해당 pdf 파일 명세서랑 현재 프로젝트 해당 명세 스펙에 맞게 구현이 되었는 지 확인해 줄 수 있을까 ? 
```

## #14 API Request 샘플링 요청
**프롬프트**
```
혹시 해당 프로젝트의 api 들을 모두 테스트 할 수 있는 request 들을 전부 짜 줄 수 있을까 ? 모든 api 들을 상세히 체크해 보고 싶어서 http request 를 모두  짜 주었으면 좋겠어 ! 
```

---

## #15 Webhook Header validation 로직 분리
**프롬프트**
```
근데 webhook header validation 을 할 떄 혹시 어노테이션있는 메서드들만 header 를 검사할 수 있도록 하는 방식은 어떨까 ? aop 를 사용하는것도 좋은 방법일 것 같은데 아니면 더 좋은 구현 방식이나 설계 방식이 있다면 추천해줘도      
  좋을 것 같아 ! 
```

---

## #16 동기 처리 로직 비동기 로직으로 변경
**프롬프트**
```
근데 내가 코드를 확인하다 보니까 우리는 지금 Webhook request 들을 동기적으로 처리를 하고 있잖아 ? 이러면 만약 대량 트래픽이 들어온다고 가정하면 서버에서 동기적으로 처리하면 문제가 생길수도 있을것 같아 보이는데 어떤것 같아 ?
  혹시 우선 요청만 받은 후 나중에 비동기로 처리하는것도 좋을것 같은데 어떄 ? 아니면 더 좋은 아키텍처나 더 좋은 처리 방식이 있다면 추천해 주었으면 좋겠어 ! PDF 명세에도 비동기로 처리하는것도 괜찮다고 나와있어서 ! 혹시 변경하는게 괜찮을것 같으면 현재 프로젝트에 테스트 코드나 리퀘스트 및 주석들도 확인 후에 변경해 줘 !
```

---

## #17 README.md 비동기 아키텍처 반영 업데이트
**프롬프트**
```
현재 구현 된 코드 기준으로 요약해서 README.md 작성해줘
```

---

## #18 테스트 커버리지 확인 요창
**프롬프트**
```
jacoco 로 테스트 커버리지 확인시에 조금 더 커버리지를 높일 수 있을 것 같아서 코드 확인 후 혹시 테스트 빠진 부분이 있으면 커버리지를 높여주었으면 좋겠어 !
```

---

## #19 HMAC verify 시 인증 유효 시간 추가
**프롬프트**
```
혹시 HMAC 인증할 때 현재는 유효 시간이 없는데 HMAC 을 생성하고 시간 제한이 있으면 더 안전할 것 같은데 어떤것 같아 ? HMAC 서명 생성 후 15 분 이내에만 유효하면 좋을 것 같은데 ? 아니면 더 안전한 방법이 있다면 추천해 주었으면 좋겠어
  ! 이 방법이 괜찮다면 코드를 구현 해줘 !    
```

---






