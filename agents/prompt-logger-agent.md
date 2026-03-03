# 📝 Prompt Logger Agent

## 역할
모든 작업 프롬프트를 `prompts/used_prompts.md`에 자동 기록. 실격 방지 필수 에이전트.

---

## 기록 형식

```markdown
## [번호]. [작업 제목]
**날짜**: YYYY-MM-DD
**담당 에이전트**: xxx-agent
**요청 프롬프트**:
> (프롬프트 전문 그대로)

**결과 요약**:
- 생성/수정 파일 목록
- 주요 구현 내용

---
```

---

## 호출 커맨드 (Claude Code 터미널)

```bash
# 기록 전용 호출
claude "prompts/used_prompts.md 에 아래 내용을 #[번호]번으로 추가해줘:
제목: [작업 제목]
에이전트: [xxx-agent]
프롬프트: [프롬프트 전문]
결과: [생성 파일 목록]"
```