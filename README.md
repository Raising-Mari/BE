# AI 제자 — BE

학생이 AI 제자를 가르치고, 친구와 제자끼리 배틀하는 서비스의 서버입니다.
Spring Boot 4 · Java 21 · MySQL · Gradle

> 코드를 고치기 전에 [AGENTS.md](AGENTS.md)(전체 규칙)와, 고치려는 폴더의 `AGENTS.md`를 먼저 읽어 주세요.

## 준비물
- JDK 21 이상 (`java -version`으로 확인)
- MySQL 8.4 이상 — 아래 둘 중 하나
  - **Docker Desktop** (WSL2 필요)
  - **설치형 MySQL**

## 처음 실행하기

### 1. 환경 파일 만들기
```bash
cp .env.example .env
```
`.env`를 열어 `DB_PASSWORD`, `DB_ROOT_PASSWORD`를 원하는 값으로 바꾸고, **`JWT_SECRET`에 32바이트 이상의 임의 문자열**을 넣습니다. (`.env`는 git에 올라가지 않습니다)
```bash
openssl rand -base64 48
```
`JWT_SECRET`이 비어 있으면 서버가 뜨지 않습니다. `GOOGLE_CLIENT_ID`는 실제 Google 로그인을 쓸 때만 필요합니다.

### 2. DB 준비 — 방법 A: Docker
```bash
docker compose up -d
```
이미 이 PC에 MySQL이 3306 포트를 쓰고 있으면 `.env`의 `DB_PORT`를 `3307`로 바꾸고 실행하세요.

### 2. DB 준비 — 방법 B: 설치형 MySQL
[scripts/local-mysql-setup.sql](scripts/local-mysql-setup.sql)을 열어 `여기에_DB_PASSWORD`를 `.env`의 `DB_PASSWORD`와 같은 값으로 바꾼 뒤, MySQL 관리자(root)로 한 번 실행합니다. (DB 두 개 `disciple`, `disciple_test`와 앱 계정이 만들어집니다)

### 3. 서버 실행
```bash
./gradlew bootRun
```
Google 없이 개발할 때는 `local` 프로필로 실행합니다. 개발용 계정(`dev1@mju.ac.kr`, `dev2@mju.ac.kr`, `admin@mju.ac.kr`)이 자동으로 만들어지고, `POST /api/auth/dev-login`에 `{ "email": "dev1@mju.ac.kr" }`을 보내면 액세스 토큰을 받습니다. 운영·시연 서버는 프로필을 지정하지 않으므로 이 경로가 없습니다.
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
- 서버: http://localhost:8080
- 연결 확인: http://localhost:8080/api/health
- API 문서(Swagger): http://localhost:8080/swagger-ui.html

### 4. 테스트
```bash
./gradlew test
```
테스트는 `disciple_test` DB를 씁니다. 실행 전에 DB가 떠 있어야 합니다.

## 폴더 구조
```
BE/
├─ src/main/java/com/aidisciple/
│  ├─ global/      공통 뼈대 (보안·에러 처리·JPA 공통)
│  ├─ ai/          외부 AI 호출 어댑터 (Gemini 등)
│  └─ domain/      기능별 코드 (auth, course, material, teaching, question,
│                  battle, grading, review, ranking)
├─ src/main/resources/
│  ├─ db/migration/  Flyway 마이그레이션 (DB 스키마는 여기서만 변경)
│  └─ prompts/       AI 프롬프트 원문 (버전 관리)
├─ src/test/        테스트
├─ docker/          Docker MySQL 초기화 스크립트
├─ scripts/         개발 보조 스크립트
└─ docs/            ERD·설계 결정·AI 사용 기록 등 팀 공유 문서
```

## 내 PC의 서버를 팀원이 쓰게 하려면
BE를 내 PC에서 띄우면 팀원 FE가 내 PC 주소로 요청을 보낼 수 있어야 합니다. 같은 와이파이가 아니라면 터널링 도구(Cloudflare Tunnel, ngrok 등)나 사설망(Tailscale)을 씁니다. 이때 `.env`의 `CORS_ALLOWED_ORIGINS`에 FE 주소를 추가해야 브라우저에서 요청이 통과합니다.
