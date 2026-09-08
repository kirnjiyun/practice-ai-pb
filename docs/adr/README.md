# ADR — 기술 의사결정 기록

"무엇을 썼는가"보다 **"왜 그것을 골랐고 무엇을 포기했는가"**를 남깁니다.
새 ADR은 [`_template.md`](_template.md)를 복사해 작성합니다.

| # | 제목 | 결정 | 상태 |
|---|---|---|---|
| [001](ADR-001-single-module.md) | 멀티모듈 대신 단일 모듈 + 패키지 경계 + ArchUnit | 단일 Gradle 모듈 | 예정 |
| [002](ADR-002-manual-asset-input.md) | 계좌 연동 대신 수기 입력 + 데모 시드 | 수기 입력 | 예정 |
| [003](ADR-003-jwt-refresh-rotation.md) | 세션 대신 JWT + Refresh Rotation | 무상태 AT + Redis RT | 예정 |
| [004](ADR-004-survey-in-db.md) | 투자성향 문항·배점을 코드가 아닌 DB로 버전 관리 | `survey_version` 테이블 | 예정 |
| [005](ADR-005-vector-db-selection.md) | Vector DB 선택 | **pgvector + HNSW** | ✅ 채택 |
| [006](ADR-006-hybrid-search.md) | 벡터 단독 대신 하이브리드 검색 + RRF | BM25 + Vector, RRF k=60 | 예정 |
| [007](ADR-007-json-schema-response.md) | LLM 응답을 자유 텍스트 대신 JSON Schema로 강제 | 스키마 + 서버 재검증 | 예정 |
| [008](ADR-008-rules-vs-llm.md) | 추천을 규칙 엔진과 LLM 설명으로 분리 | 규칙=판단, LLM=설명 | 예정 |
| [009](ADR-009-frontend-state.md) | RTK 대신 TanStack Query + Zustand | Query + Zustand | 예정 |
| [010](ADR-010-async-without-broker.md) | Kafka 대신 `@Async` + DB 상태 | Spring @Async | 예정 |

### 후보 (여유 시 추가)

- ADR-011 SSE vs WebSocket
- ADR-012 소유권 검증을 Application Service 계층에 배치한 이유
- ADR-013 감사 로그 append-only 강제 방식
