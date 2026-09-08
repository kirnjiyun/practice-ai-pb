# 장애 대응 런북

## 설계 원칙 3가지

```
1. 부분 실패 허용 (Graceful Degradation)
   AI가 죽어도 대시보드·자산·목표·규칙 기반 추천·위험 알림은 100% 동작한다.

2. 안전 우선 폴백 (Fail-Safe, not Fail-Open)
   불확실할 때 답을 지어내지 않는다.
   검색 실패 → 추측 금지 REFUSE.  Rate limiter 장애 → 차단(fail-closed).

3. 버전 스위치로 즉시 롤백
   PROMPT_VERSION / POLICY_VERSION / RULE_VERSION 은 설정값이다.
   품질 사고 시 재배포 없이 이전 버전으로 되돌린다.
```

## 대응 절차

1. 알림 수신 → Grafana `서비스 헬스` 확인 → 영향 범위 판정 (전체 / AI만 / 문서만)
2. `requestId`로 로그 추적
3. AI 관련이면 `ai_audit_log`에서 `fallback_level` 분포 확인 → 원인 계층 특정
4. 조치 → 재현 테스트 → 회고 기록 (`docs/postmortem/`)

### fallback_level로 원인 계층 좁히기

| 급증 레벨 | 원인 계층 | 1차 조치 |
|---|---|---|
| L1 | 검색 / 문서 | 문서 승인 상태, HNSW 인덱스, 임베딩 모델 일치 확인 |
| L2 | LLM 지연 | 타임아웃·서킷 브레이커 상태 확인 |
| L4 | LLM / 스키마 | `PROMPT_VERSION` 이전 버전으로 롤백 검토 |
| L5 | 출력 게이트 오탐 | `POLICY_VERSION` 이전 버전으로 롤백 검토 |

## 장애별 Fallback

| 장애 | 영향 | Fallback | 복구 |
|---|---|---|---|
| LLM API 다운 | AI 상담 불가 | 서킷 브레이커(실패율 50%/1분 → 60초 open) → L4 폴백 카드 + 규칙 기반 학습 콘텐츠 3건. **추천은 템플릿 폴백으로 정상 동작** | half-open 자동 탐색 |
| LLM 응답 지연 | 사용자 대기 | 전체 20s / idle 10s 타임아웃 → L2 → L4 | — |
| Embedding API 다운 | 신규 ingestion 불가 (검색은 정상) | `status=FAILED` + 재시작 큐 | `POST .../reingest` |
| Vector 검색 실패·지연 | 근거 확보 불가 | 400ms 타임아웃 → BM25 단독 → 부족하면 **L1 REFUSE** | 인덱스 재구축 |
| Redis 다운 | 캐시·RL·세션 | 캐시 miss → DB 직접 조회(기능 유지). **Rate limiter는 fail-closed** (상담만 일시 차단) | 재기동 후 자동 회복 |
| DB 다운 | 전체 불가 | 503 + 상태 안내 | 컨테이너·볼륨 확인 |
| MinIO 다운 | 업로드 불가 (상담·검색 정상) | 업로드 API만 503 | — |
| 스키마 검증 실패율 > 10% (5분) | 답변 품질 저하 | AI 상담 **degraded 모드** — 안내 배너 + 규칙 기반 콘텐츠만 | 프롬프트 롤백 |
| 정책 게이트 오탐 급증 | 정상 질문 차단 | `POLICY_VERSION` 롤백 스위치 (설정 변경, 배포 불필요) | 패턴 사전 수정 후 재배포 |

## 롤백 명령

```bash
# 프롬프트만 이전 버전으로
docker compose --profile app up -d --no-deps -e PROMPT_VERSION=v1 api

# 정책만 이전 버전으로
docker compose --profile app up -d --no-deps -e POLICY_VERSION=v1 api
```

## 확인용 쿼리

```sql
-- 최근 1시간 폴백 레벨 분포
SELECT fallback_level, count(*)
FROM ai_audit_log
WHERE created_at > now() - interval '1 hour'
GROUP BY 1 ORDER BY 1;

-- 스키마 검증 실패율
SELECT
  count(*) FILTER (WHERE NOT schema_valid)::numeric / nullif(count(*), 0) AS invalid_rate
FROM ai_audit_log
WHERE created_at > now() - interval '15 minutes';

-- 승인되지 않은 문서가 인용된 적이 있는지 (0건이어야 정상)
SELECT count(*)
FROM message_source ms
JOIN document_chunk dc ON dc.id = ms.document_chunk_id
JOIN document_version dv ON dv.id = dc.document_version_id
WHERE dv.status <> 'APPROVED';
```
