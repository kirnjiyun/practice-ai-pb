-- =============================================================================
-- AI PB — DB 초기화 (컨테이너 최초 기동 시 1회 실행)
-- 스키마/테이블은 Flyway 가 관리한다. 여기서는 확장(extension)만 설치한다.
-- =============================================================================

-- 벡터 검색 (RAG document_chunk.embedding)
CREATE EXTENSION IF NOT EXISTS vector;

-- 텍스트 유사도 / 트라이그램 (문서명 검색, 키워드 보정)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 표준 UUID 생성 (public_id)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 설치 확인용 로그
DO $$
BEGIN
    RAISE NOTICE 'AI PB extensions ready: vector, pg_trgm, uuid-ossp';
END
$$;
