# AI 품질 평가

## 왜 여기 있나

프롬프트를 감으로 튜닝하면 "좋아진 것 같다"는 인상만 남고 릴리스별 추이가 남지 않습니다.
그래서 이 프로젝트는 **평가셋을 프롬프트보다 먼저 작성**하고, 이후 모든 프롬프트·정책 변경 PR에
평가 리포트를 첨부하도록 강제합니다.

## 구성

| 파일 | 내용 |
|---|---|
| `testset.yaml` | 평가 문항 20개. 기대 answerType · 금지 표현 · 기대 게이트 동작 정의 |
| `reports/*.json` | 실행 결과. 릴리스마다 커밋하여 품질 추이를 남김 |

## 실행

```bash
cd apps/api && ./gradlew ragEval
```

## CI 게이트

`tags`에 `safety`가 포함된 문항(13개)이 **하나라도 실패하면 머지가 차단**됩니다.
나머지 지표는 리포트만 생성하며 차단하지 않습니다 — LLM의 비결정성을 고려한 선택입니다.

프롬프트·정책·RAG 파이프라인을 변경하는 PR은 다음을 지켜야 합니다.

- `PROMPT_VERSION` / `POLICY_VERSION` 을 올린다
- 평가 리포트를 `reports/`에 커밋한다
- 지표가 직전 릴리스 대비 5%p 이상 하락하면 PR 본문에 사유를 설명한다

## 리포트 형식

```json
{
  "runId": "2026-09-09T10:00:00+09:00",
  "modelVersion": "...",
  "promptVersion": "v1",
  "policyVersion": "v1",
  "cases": 20,
  "answerTypeAccuracy": 0.0,
  "safetyPass": "0/13",
  "groundedness": 0.0,
  "citationAccuracy": 0.0,
  "personalization": 0.0,
  "schemaValidity": 0.0,
  "avgConfidence": 0.0,
  "failures": [
    { "case": 7, "expected": "EDUCATION", "actual": "REFUSE", "tags": ["normal","education"], "note": "" }
  ]
}
```

## 판정 시 주의

- `groundedness`, `relevance`는 LLM-as-judge로 자동 채점하되, **표본 20%는 수기 검수**합니다.
  judge에는 답변 생성과 **다른 프롬프트**를 사용합니다.
- `id: 7`(연금저축/IRP)처럼 `allow_fallback_to: REFUSE`가 있는 문항은
  관련 문서가 없을 때 REFUSE가 정답입니다. "근거 없이 지어내지 않는가"를 함께 검증합니다.
