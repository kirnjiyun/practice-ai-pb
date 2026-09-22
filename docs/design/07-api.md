# API 명세 — W2

Base URL: `http://localhost:8080`. 요청과 응답은 JSON이다.

| Method | Path | 권한 | 요청 | 성공 |
|---|---|---|---|---|
| POST | `/api/auth/register` | 공개 | email, password | 201 사용자 |
| POST | `/api/auth/login` | 공개 | email, password | 200 토큰 쌍 + 사용자 |
| POST | `/api/auth/refresh` | 공개 | refreshToken | 200 새 토큰 쌍 + 사용자 |
| POST | `/api/auth/logout` | 토큰 소유 | refreshToken | 204 |
| GET | `/api/users/me` | 인증 | Bearer accessToken | 200 사용자 |
| GET | `/api/pb/me` | PB, ADMIN | Bearer accessToken | 200 사용자 |
| GET | `/api/admin/me` | ADMIN | Bearer accessToken | 200 사용자 |
| GET | `/actuator/health` | 공개 | 없음 | 200 상태 |

사용자 응답: `{ "id": "UUID", "email": "user1@aipb.demo", "role": "USER" }`.
로그인/갱신 응답에는 `accessToken`, `refreshToken`, `tokenType: "Bearer"`,
`expiresIn`(초), `user`가 포함된다. `Cache-Control: no-store`를 설정한다.

갱신 성공 시 이전 토큰 쌍을 모두 교체해야 한다. 로그아웃은 refreshToken으로 해당 세션만 폐기한다.
PB/관리자 `/me`는 현재 권한 확인용이며 고객 목록/문서 관리 API는 아직 구현하지 않았다.

OpenAPI 및 Swagger UI는 후속 구현 예정이다.
