# global/ — 공통 뼈대

모든 도메인이 함께 쓰는 코드만 둔다. **도메인 로직(채점, 노트, 배틀 등)은 여기에 두지 않는다.**

## 규칙
- `global/`은 `domain/`·`ai/`를 import하지 않는다. (의존 방향: `domain` → `global`, 반대는 금지)
- 하위 폴더: `config/`(스프링 설정) · `error/`(에러 처리) · `jpa/`(엔티티 공통) · `health/`(연결 확인) · `api/`(페이지네이션) · `security/`(로그인 사용자 타입) · `ratelimit/`(사용량 제한)
- 두세 도메인이 똑같은 코드를 쓰게 될 때만 여기로 올린다. 처음부터 "나중에 쓸지도 몰라서" 만들지 않는다.
- 여기를 고치면 모든 팀원에게 영향이 가므로 PR 설명에 무엇이 달라지는지 적는다.

## 공통 도구 사용법
- **목록 API**: 컨트롤러가 `Pages.of(page, size)`로 `Pageable`을 만들고(`size` 기본 20, 최대 50), 응답은 `PageResponse.from(page, mapper)`로 만든다.
- **로그인 사용자**: 컨트롤러 인자에 `@CurrentUser AuthUser user`. 역할 검사는 `/api/admin/**`가 경로 규칙으로 이미 막는다.
- **사용량 제한**: `RateLimiter.check(정책이름, rateLimitProperties.xxx(), 키)`. 초과하면 `RATE_LIMITED`(429). 한도는 `app.rate-limit.*`.
- **현재 시각**: `Instant.now()` 대신 `Clock`을 주입받는다.
- **비동기**: `@Async`는 `taskExecutor`(크기 제한)를 쓴다. 큐가 차면 예외가 나므로 작업 상태를 DB에 실패로 남긴다.
