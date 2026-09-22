# 데이터 모델 — W2/W3 구현 범위

| 테이블 | 목적 | 주요 제약 |
|---|---|---|
| app_user | 계정 및 역할 | UUID PK, email unique, BCrypt 해시 |
| refresh_session | 토큰 세션 | user_id FK, token_hash unique, expires_at, revoked |
| investment_profile | 투자성향 진단 이력 | user_id FK, 버전·응답·점수·분류·진단/만료 시각 |
| asset | 가상 자산·부채 | user_id FK, 종류 CHECK, 양수 원화 정수, version |

Flyway V1은 인증 테이블, V2는 진단·자산 테이블을 생성한다.
진단은 user_id/assessed_at 인덱스로 최신 결과를 조회한다.
자산은 user_id 인덱스를 사용하고 모든 객체 접근에 소유자 조건을 적용한다.
자산 version은 JPA 낙관적 잠금과 클라이언트 version 확인에 사용한다.
시간은 UTC Instant, 금액은 NUMERIC(15,0)/BigDecimal로 다룬다.

추천·목표·RAG·상담·감사 로그 테이블은 아직 없다.
