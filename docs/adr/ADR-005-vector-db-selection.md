# ADR-005: Vector DB로 pgvector 선택

- 상태: 채택
- 날짜: 2026-09-09

## 맥락

AI PB의 RAG 검색은 유사도 계산만으로 끝나지 않습니다. 모든 검색은 다음 네 조건을
**빠짐없이** 만족해야 합니다.

```
승인된 문서만          document_version.status = 'APPROVED'
사용자 역할이 허용됨    document.role_access @> [요청자 역할]
테넌트 일치            document.tenant_id = 요청자 테넌트
아카이브/삭제 제외      chunk.archived_at IS NULL AND document.deleted_at IS NULL
```

이 중 하나라도 누락되면 **미승인 문서나 권한 밖 문서가 답변 근거로 인용되는 사고**가 됩니다.
금융 상담 도메인에서 이는 성능 저하보다 훨씬 치명적입니다.

한편 MVP 규모는 문서 수백 건, 청크 수만 개 수준으로, 벡터 검색 엔진의 처리량이
병목이 될 구간이 아닙니다.

## 검토한 대안

| 후보 | 장점 | 단점 |
|---|---|---|
| **pgvector** | PostgreSQL 단일 인프라. 메타데이터 필터와 벡터 검색을 하나의 SQL·하나의 트랜잭션으로 처리. BM25(tsvector)까지 같은 DB에서 하이브리드 구성 가능. 백업·권한·마이그레이션 체계 공유 | 초대규모(수천만 벡터)에서 전용 엔진 대비 성능 열세 |
| Qdrant | 필터링 성능 우수, payload 인덱스 강력, 운영 API 풍부 | 컨테이너 1개 추가. 문서 승인 상태 등 관계형 데이터와의 **정합성을 애플리케이션이 책임**져야 함 |
| Chroma | 로컬 프로토타이핑 간편 | 운영 성숙도·인증·권한 기능 부족 |

## 결정

**pgvector + HNSW 인덱스**를 채택합니다.

단, `VectorSearchRepository` 인터페이스로 추상화하여 `PgVectorSearchRepository` →
`QdrantSearchRepository` 교체가 **구현체 1개 추가로 끝나도록** 설계합니다.

```java
public interface VectorSearchRepository {
    // 호출자가 권한 필터를 생략할 수 없도록 시그니처에 강제한다.
    List<ChunkHit> search(EmbeddingVector query, SearchFilter filter, int topN);
}
```

## 근거

1. **필터 누락이 즉시 드러난다.** 권한·승인 조건이 SQL `WHERE`에 그대로 표현되므로
   리뷰와 통합 테스트에서 누락을 잡아낼 수 있습니다. 별도 엔진이면 필터가 애플리케이션
   코드로 흩어져 조용히 빠질 여지가 생깁니다.
2. **승인과 검색 가능 상태를 한 트랜잭션으로 묶습니다.** 관리자가 문서를 승인/반려할 때
   상태 변경과 인덱스 가시성이 원자적으로 처리됩니다. "승인 안 된 문서가 검색되는" 사고를
   구조적으로 차단합니다.
3. **하이브리드 검색이 같은 DB 안에서 끝납니다.** 벡터(HNSW)와 키워드(tsvector GIN)를
   한 쿼리로 조회하고 RRF로 융합할 수 있어, 두 저장소 간 정합성 문제가 없습니다.
4. **재현성.** 인프라 컴포넌트가 하나 줄어 `docker compose up` 기동 시간과 포트폴리오
   재현 성공률이 올라갑니다. 이 프로젝트에서 재현성은 기능만큼 중요합니다.

## 결과 / 트레이드오프

- (+) 미승인·권한 밖 문서 노출을 구조적으로 차단. 통합 테스트로 검증 가능
- (+) 인프라 단순화, 백업·마이그레이션 일원화
- (−) 수천만 벡터 규모에서는 Qdrant 대비 성능 열세
- (−) HNSW 인덱스 파라미터(`m`, `ef_search`) 튜닝을 직접 해야 함

**전환 조건**: 아래 중 하나라도 지속되면 Qdrant 도입을 재검토합니다.

- 청크 수 100만 건 초과
- RAG 검색 p95 > 500ms 가 1주 이상 지속
- 메타데이터 필터 조합이 인덱스 선택을 방해해 seq scan 이 빈발

## 초기 인덱스 설정

```sql
CREATE INDEX idx_chunk_embedding
    ON document_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_chunk_tsv
    ON document_chunk USING gin (content_tsv);

CREATE INDEX idx_chunk_meta
    ON document_chunk USING gin (metadata jsonb_path_ops);

-- 검색 세션에서
SET hnsw.ef_search = 64;
```
