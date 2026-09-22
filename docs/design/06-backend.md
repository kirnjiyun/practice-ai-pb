# 백엔드 아키텍처 — W2 인증

## 구현 범위

Java 21 / Spring Boot 3.5 기반 API. `com.aipb.auth`에 인증 관련 컨트롤러,
서비스, JPA 저장소, Security 설정을 배치한다. Flyway V1이 사용자와 세션 테이블을
생성하며 Hibernate는 스키마를 검증만 한다.

## 인증과 권한

- 회원가입은 USER만 생성한다. 요청에 전달한 role은 권한 부여에 사용하지 않는다.
- 이메일은 소문자로 정규화하며 DB unique 제약으로 중복 가입 경쟁도 차단한다.
- 비밀번호는 BCrypt로 저장한다. 가입 시 10자 이상, UTF-8 72바이트 이하를 요구한다.
- Access JWT는 HS256 서명, issuer/만료/세션 유효성을 확인한다. 기본 수명 30분.
- Refresh token은 SecureRandom 32바이트의 Base64URL 값이며 DB에는 SHA-256 해시만 저장한다.
- 기본 refresh 수명은 발급 시점부터 14일이다. 갱신할 때 새 세션을 생성하므로 수명은 연장된다.
- 갱신 시 기존 행을 비관적 잠금으로 읽고 폐기한다. 같은 토큰의 동시 갱신은 하나만 성공한다.
- 로그아웃/갱신 시 기존 access token도 즉시 사용할 수 없도록 요청마다 세션 상태를 조회한다.
- 탈취 토큰의 재사용은 거절하지만, 세션 계보 전체 폐기 정책은 아직 구현하지 않았다.
- `/api/admin/**`는 ADMIN, `/api/pb/**`는 PB 또는 ADMIN, 나머지 `/api/**`는 인증이 필요하다.
- 토큰은 쿠키로 인증하지 않고 Authorization 헤더/JSON 본문으로 명시적으로 전달한다.
  따라서 CSRF를 비활성화하고 CORS는 설정된 Origin만 허용한다.
- 웹은 토큰을 메모리에만 저장한다. 새로고침 시 재로그인하며 동시 401 응답은 하나의 갱신 요청을 공유한다.

## 실패 처리

입력 형식 오류 400, 인증 실패/만료/재사용 401, 역할 불일치 403, 중복 계정 409.
로그인은 계정 존재 여부와 무관하게 동일한 인증 오류를 반환한다.
로그아웃은 이미 폐기되거나 없는 형식상 유효한 토큰에 대해서도 204를 반환한다.
API는 비밀번호/토큰을 응답 로그에 기록하지 않는다.

## 실행 및 검증

저장소 루트 `.env`는 Compose에서만 자동으로 읽힌다. `bootRun`으로 실행할 때는
DB_URL/DB_USER/DB_PASSWORD/JWT_SECRET을 프로세스 환경 변수로 지정한다.
로컬 직접 실행 DB_URL은 `jdbc:postgresql://localhost:5432/aipb`이다.

```powershell
cd apps/api
.\gradlew.bat build
# PostgreSQL과 환경 변수를 준비한 뒤
.\gradlew.bat bootRun
```

`SPRING_PROFILES_ACTIVE=local`과 `DEMO_SEED_ENABLED=true`를 함께 설정해야 데모 계정이 생성된다.
JWT_SECRET은 예시의 change-me 값을 허용하지 않는다. 충분히 긴 난수로 교체한다.

인증 통합 테스트는 H2의 PostgreSQL 호환 모드에서 실제 Flyway SQL과 Security 필터를 사용한다.
운영 DB의 잠금/타입 동작은 PostgreSQL 환경에서 별도 검증해야 한다.

## 다음 보완

로그인 시도 제한, 만료/폐기 세션 정리, 이메일 검증, 비밀번호 재설정,
토큰 계보 폐기, 실제 PostgreSQL 통합 테스트는 후속 보완 사항이다.
Redis/PII 암호화/QueryDSL은 아직 연결하지 않았다. 회원가입에는 가상 이메일만 사용한다.
