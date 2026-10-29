# src/main/resources/ — 설정과 리소스

## 규칙
- `application.yml`은 공통 설정만 둔다. **비밀값은 절대 쓰지 않는다.** (비밀번호·API 키는 `${ENV_NAME}` 으로만 받는다)
- 설정을 새로 추가하면 같은 PR에서 `.env.example`과 README(필요 시)를 고친다.
- 내 PC에서만 쓰는 설정은 `application-local.yml`에 두면 되고, 이 파일은 `.gitignore`로 제외돼 있다.
- 우리가 만드는 설정 키는 `app.*` 아래에 둔다. (예: `app.cors.*`, `app.ai.gemini.*`)
- 이 서버는 API 전용이다. `templates/`·`static/` 폴더는 만들지 않는다.
- 하위 폴더: `db/migration/`(Flyway) · `prompts/`(AI 프롬프트)
