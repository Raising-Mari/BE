# domain/auth — 로그인·권한

## 책임
학교 Google 계정으로 로그인 → 처음이면 닉네임·학과를 정해 가입 → 서버가 액세스 JWT(7일) 발급·검증. 비밀번호와 메일 인증 코드는 없다.
설계: [api-spec.md](../../../../../../../docs/api-spec.md) 1절, 사용자 테이블: [erd.md](../../../../../../../docs/erd.md) 6-1.

## 구조
- `security/` — `JwtProvider`(발급·검증), `JwtAuthenticationFilter`(Bearer 토큰 → 로그인 상태). `SecurityConfig`는 이 필터를 **빈 이름(`jwtAuthenticationFilter`)으로만** 받아 연결한다.
- `service/GoogleIdentityVerifier` — Google ID 토큰 확인 경계. 구현은 `GoogleApiIdentityVerifier`. 테스트에서는 가짜 구현을 꽂는다.
- `service/DevAuthService`, `controller/DevLoginController`, `service/LocalSeedUserInitializer` — **`local` 프로필 전용**. 운영·시연 프로필에는 빈이 없어 `/api/auth/dev-login`이 404다.

## 규칙
- 허용 메일 도메인은 DB(`schools.email_domain`)가 기본이고, 코드에 박지 않는다. Google ID 토큰의 `hd`와 비교한다.
- Google ID 토큰은 로그인 호출에서만 쓰고 **저장하지 않는다.** 검증은 외부 호출이므로 DB 트랜잭션 안에서 하지 않는다.
- 토큰마다 `purpose` 클레임을 둔다. 가입 토큰처럼 다른 용도의 토큰을 만들 때는 다른 `purpose`를 써서 액세스 토큰으로 통하지 못하게 한다.
- 액세스 토큰에는 사용자 id와 역할만 담긴다. 요청마다 DB를 조회하지 않으므로, 탈퇴·권한 변경이 즉시 반영돼야 하는 곳은 서비스에서 다시 조회한다.
- `JWT_SECRET`은 `.env`에만 둔다. 32바이트 미만이면 서버가 뜨지 않는다. 로그에 토큰·키를 남기지 않는다.
- 응답에는 이메일 전체를 돌려주지 않는다. 필요하면 마스킹한다(`a***@mju.ac.kr`).
- 닉네임은 중복·부적절 표현을 검사한다.
- 컨트롤러에서 로그인 사용자는 `@CurrentUser AuthUser`로 받는다.
