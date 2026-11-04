# global/config/ — 스프링 설정 클래스

- 설정 클래스는 `@Configuration` 하나에 관심사 하나만 담는다. (보안 / OpenAPI / JPA …)
- **`SecurityConfig`의 `PUBLIC_PATHS`는 인증 없이 열리는 경로의 단일 목록**이다. 경로를 추가할 때는 PR에서 이유를 설명한다.
- 설정 값은 하드코딩하지 않고 `application.yml`의 `app.*` 키로 받는다. (`@Value` 또는 `@ConfigurationProperties`)
- CORS 허용 주소는 `.env`의 `CORS_ALLOWED_ORIGINS`로만 바꾼다. `*`(전체 허용)로 바꾸지 않는다.
- JWT 필터 등 인증 로직은 `domain/auth`에 있고, 이 폴더에서는 빈 이름(`jwtAuthenticationFilter`)으로 받아 필터 체인에 연결만 한다. (`global`이 `domain`을 import하지 않기 위함)
- `/api/admin/**`은 `ADMIN`만 허용한다. 401·403도 `ErrorResponse` JSON으로 답한다(`SecurityErrorHandler`).
