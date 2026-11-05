# domain/auth — 로그인·권한

## 책임
학교 Google 계정으로 로그인 → 처음이면 닉네임·학과를 정해 가입 → 서버가 액세스 JWT(7일) 발급·검증. 비밀번호와 메일 인증 코드는 없다.
설계: [api-spec.md](../../../../../../../docs/api-spec.md) 1절, 사용자 테이블: [erd.md](../../../../../../../docs/erd.md) 6-1.

## 구조
- `controller/` — A-01·A-03·A-04(`AuthController`), A-05(`DepartmentController`), A-06·A-07(`MeController`), A-09(`DevLoginController`, `local` 전용)
- `service/`
  - `GoogleLoginService` — A-01. **트랜잭션을 걸지 않는다.** Google 서버 호출 동안 DB 커넥션을 쥐지 않기 위해서다. DB 쓰기는 `UserAccountService`가 짧은 트랜잭션으로 한다.
  - `SignupService` — A-03·A-04. `MeService` — A-06·A-07. `DepartmentService` — A-05.
  - `GoogleIdentityVerifier` — Google ID 토큰 확인 경계(구현 `GoogleApiIdentityVerifier`). 테스트에서는 가짜를 꽂는다.
  - `DevAuthService`·`LocalSeedUserInitializer` — **`local` 프로필 전용**. 운영·시연 프로필에는 빈이 없어 `/api/auth/dev-login`이 404다.
- `security/` — `JwtProvider`(액세스 토큰·가입 토큰), `JwtAuthenticationFilter`. `SecurityConfig`는 이 필터를 **빈 이름(`jwtAuthenticationFilter`)으로만** 받아 연결한다.
- `logic/` — DB를 모르는 순수 클래스: `NicknamePolicy`(형식·사칭 이름), `EmailMasker`.
- `config/` — `AuthProperties`(`app.auth.*`), `AdminProperties`(`app.admin.emails`)

## 규칙
- 학교는 Google ID 토큰의 `hd`로 `schools.email_domain`에서 찾는다. `hd`가 없거나 등록되지 않은 도메인이면 `AUTH_EMAIL_DOMAIN_NOT_ALLOWED`. 코드에 도메인을 박지 않는다.
- Google ID 토큰은 로그인 호출에서만 쓰고 **저장하지 않는다.** 검증은 외부 호출이므로 DB 트랜잭션 안에서 하지 않는다.
- 토큰마다 `purpose` 클레임이 있다(`access`, `signup`). 서로 바꿔 쓸 수 없다. 새 용도의 토큰은 새 `purpose`를 쓴다.
- 가입 토큰(10분)은 Google이 확인해 준 계정 정보를 가입 요청까지 나른다. 가입 요청 본문의 이메일·학교는 믿지 않고 **토큰 값만** 쓴다. "1회용"은 `users.google_sub` UNIQUE와 서비스의 중복 검사가 지킨다(토큰 자체는 상태가 없다).
- 액세스 토큰에는 사용자 id와 역할만 담긴다. 요청마다 DB를 조회하지 않으므로, 탈퇴·권한 변경이 즉시 반영돼야 하는 곳은 서비스에서 다시 조회한다(`/api/me`는 사용자가 없거나 탈퇴했으면 401).
- 관리자는 `ADMIN_EMAILS`에 적힌 메일로 **가입할 때** 정해진다. 이미 가입한 계정의 역할은 바꾸지 않는다.
- `JWT_SECRET`은 `.env`에만 둔다. 32바이트 미만이면 서버가 뜨지 않는다. 로그에 토큰·키를 남기지 않는다.
- 응답에는 이메일 전체를 돌려주지 않는다. `a***@mju.ac.kr`처럼 가린다.
- 닉네임은 2~12자(한글·영문·숫자·`_`)이고 대소문자 구분 없이 유일하다. 운영진 사칭 이름(`admin`·`관리자` 등)은 막는다. 욕설 사전은 아직 없다.
- 컨트롤러에서 로그인 사용자는 `@CurrentUser AuthUser`로 받는다.
