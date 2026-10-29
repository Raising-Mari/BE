# BE (백엔드) 규칙

AI 제자 서비스의 서버. Spring Boot 4 · Java 21 · MySQL 8.4 · Gradle.
이 파일은 BE 전체에 적용된다. **폴더마다 `AGENTS.md`가 따로 있으니, 그 폴더의 파일을 고치기 전에 해당 `AGENTS.md`를 먼저 읽는다.**
(Claude Code는 `CLAUDE.md`, Codex는 `AGENTS.md`를 읽는다. 내용은 `AGENTS.md` 한 곳에서만 고친다.)

## 실행 명령어
```bash
cp .env.example .env        # 최초 1회. 값(DB 비밀번호 등)을 채운다
docker compose up -d        # MySQL 실행 (Docker 를 쓰는 경우. 설치형 MySQL 이면 README 참고)
./gradlew bootRun           # 서버 실행 → http://localhost:8080
./gradlew test              # 테스트 (docker compose up -d 필요)
./gradlew build             # 전체 빌드
```
- API 문서(Swagger): http://localhost:8080/swagger-ui.html · 명세 원본: `/v3/api-docs`
- 연결 확인: `GET /api/health`

## 패키지 구조 (`src/main/java/com/aidisciple`)
| 폴더 | 역할 |
|---|---|
| `global/` | 모든 도메인이 쓰는 공통 뼈대 (보안·설정·에러·JPA 공통) |
| `ai/` | 외부 AI(Gemini 등) 호출을 모아 둔 어댑터. **AI 호출은 여기서만** |
| `domain/<이름>/` | 기능별 코드. 도메인 하나 = 폴더 하나 |

## 반드시 지키는 규칙
1. **계층**: `Controller → Service → Repository`. Controller는 요청·응답 변환만, 로직은 Service에 둔다.
2. **DTO**: API 요청·응답에는 `record` DTO만 쓴다. **엔티티를 API로 직접 받거나 돌려주지 않는다.**
3. **도메인 경계**: 다른 도메인의 Repository·엔티티 내부를 직접 만지지 않고, 그 도메인의 Service를 호출한다.
4. **에러**: 예상 가능한 오류는 `BusinessException(ErrorCode)`를 던진다. 성공 응답은 감싸지 않고 DTO를 그대로 반환한다.
5. **트랜잭션**: Service 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`.
6. **DB 스키마는 Flyway만 바꾼다.** `ddl-auto`는 `validate`로 고정. 엔티티만 고치고 마이그레이션을 빼먹으면 서버가 뜨지 않는다.
7. **시각은 UTC `Instant`**로 저장·전송한다. 한국 시간 변환은 FE가 한다. 엔티티는 `BaseTimeEntity`를 상속한다.
8. **비밀값(DB 비밀번호, API 키)은 `.env`에만** 둔다. 코드·yml·로그·PR에 넣지 않는다. 새 설정 키를 추가하면 `.env.example`도 같이 고친다.
9. **로그에 개인정보·학생이 입력한 본문·API 키를 남기지 않는다.**
10. **AI 결과는 그대로 믿지 않는다.** LLM 출력은 코드로 검증한 뒤에만 DB에 반영한다. (제안서의 핵심 원칙)
11. **학생에게 숨겨야 하는 것**(문제 은행의 문항·정답·개념 목록)은 학생용 응답 DTO에 아예 필드로 두지 않는다.
12. **API 경로는 `/api/...`**로 시작하고, 인증이 필요 없는 경로는 `global/config/SecurityConfig`의 `PUBLIC_PATHS`에서만 추가한다.

## 코드 스타일
- 들여쓰기는 탭(Java), 2칸 스페이스(yml). `.editorconfig`를 따른다.
- 엔티티: `@Getter` + `@NoArgsConstructor(access = PROTECTED)`. **엔티티에 `@Data`·`@Setter` 금지.** 상태 변경은 의미 있는 메서드로 한다.
- 테이블명은 `snake_case` **복수형**(`users`, `note_entries`), 컬럼은 `snake_case`.
- 주석은 "왜 이렇게 했는지"만 적는다. 코드를 그대로 풀어쓴 주석은 쓰지 않는다.
- Spring Boot 4 / Spring Security 7 / Jackson 3 기준이다. Boot 2·3 시절 예제를 복사하면 패키지나 API가 다를 수 있으니 반드시 컴파일해서 확인한다.

## 테스트
- 로직(검증·채점·Elo 계산)은 DB·AI 없이 돌아가는 **순수 단위 테스트**를 먼저 쓴다.
- DB가 필요한 테스트는 `@ActiveProfiles("test")`로 테스트 전용 DB(`disciple_test`)를 쓴다.
- 테스트에서 실제 AI를 호출하지 않는다. `ai/`의 인터페이스에 가짜 구현을 꽂는다.

## Git / 협업
- 브랜치: `main`(배포) ← `develop`(통합) ← `feat/<이슈번호>-<설명>`. `develop`·`main`에 직접 push하지 않는다.
- 커밋: `feat:` `fix:` `refactor:` `docs:` `test:` `chore:` 로 시작한다.
- PR: 작게 쓰고 1명 이상 리뷰 후 머지한다. **API·DB를 바꾸는 PR은 FE 담당에게 알린다.**
- AI 도구(Claude Code·Codex)로 만든 코드는 PR 설명에 "AI 사용: 도구 / 용도"를 한 줄 남긴다. (대회 제작 기록 증빙)
