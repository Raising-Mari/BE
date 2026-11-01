# API·핵심 로직 설계

> **상태: 설계 초안 (구현 전).** 구현이 시작되면 API의 원본은 Swagger(`/v3/api-docs`)이고, 이 문서는 착수용 계약서다.
> API를 바꿀 때는 Swagger를 먼저 고치고 이 문서와 어긋난 부분은 지우거나 갱신한다.
> DB는 [erd.md](erd.md), 작업 순서는 [backend-plan.md](backend-plan.md).
>
> **이 문서의 수명**: 지금(구현 전)은 이 문서가 계약서다. Swagger가 올라오면 엔드포인트 표와 요청·응답 예시는 지우고 11절(핵심 로직)과 12절(오류 코드)만 남긴다. (BE `AGENTS.md`의 "API 명세의 원본은 Swagger" 규칙과 같은 방향이다.)

## 0. 규약

| 항목 | 규칙 |
|---|---|
| 기본 경로 | `/api/...`, JSON(UTF-8), 필드는 `camelCase` |
| 인증 | `Authorization: Bearer <accessToken>`. 표의 **인증** 열: `공개`=없음, `로그인`=학생, `관리자`=`role=ADMIN` |
| 시각 | ISO-8601 UTC 문자열 (`2026-11-02T01:23:45.678Z`) |
| ID | 숫자(Long). **모든 ID 경로는 소유·참가 여부를 서버가 검사한다** (남의 제자·배틀·노트 접근 방지) |
| 성공 응답 | DTO를 감싸지 않고 그대로. 생성은 `201`, 비동기 접수는 `202`, 본문 없음은 `204` |
| 오류 응답 | `{ "code": "BATTLE_ROOM_FULL", "message": "...", "violations": [{"field","reason"}] }` |
| 페이지네이션 | 요청 `page`(0부터)·`size`(기본 20, 최대 50). 응답 `{ "items": [], "page": 0, "size": 20, "totalElements": 0, "hasNext": false }` |
| 우선순위 | **P0** 핵심 루프(없으면 시연 불가) · **P1** 제안서가 약속한 범위(11/12 목표) · **P2** 여유 있을 때 |
| WP | [backend-plan.md](backend-plan.md)의 작업 묶음 번호 (표의 "6"은 6-A·6-B를 합친 것) |
| 번호 | `A-01`·`R-11` 같은 번호는 다른 문서가 번호로 가리키므로, 중간에 빠진 번호가 있어도 다시 매기지 않는다 |

**학생에게 절대 내려가지 않는 것**: 개념 목록(`concepts`), 배틀 시작 전의 문항·정답, **정답 보기(R-11)를 누르기 전의 모범 답과 자료 근거 문장**(끝난 배틀이어도), 상대 제자의 답과 노트(**양쪽이 모두 공개에 동의하기 전**), `battle_answers.grading_detail_json`의 내부 패턴 목록. DTO에 필드 자체를 두지 않는다.

**용어**: API와 DB의 **단원(`unit`)은 곧 주차**다. 학생 화면에서는 "주차"로 부른다.

---

## 1. 인증 (`domain/auth`)

로그인 방식은 **학교 Google 계정**이다 (비밀번호·메일 코드 없음). FE가 Google 로그인으로 받은 **ID 토큰**을 서버에 보내면 서버가 검증하고, 이미 가입한 사람이면 바로 로그인, 처음이면 닉네임·학과를 정하고 가입한다.

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| A-01 | `POST /api/auth/google` | 공개 | Google ID 토큰 확인 → 로그인 또는 가입 토큰 | P0 | 3 |
| A-03 | `POST /api/auth/signup` | 공개 | 닉네임·학과 정하고 가입 | P0 | 3 |
| A-04 | `GET /api/auth/nicknames/availability` | 공개 | 닉네임 중복 확인 | P0 | 3 |
| A-05 | `GET /api/departments` | 공개 | 가입 화면용 학과 목록(단과대 포함) | P0 | 3 |
| A-06 | `GET /api/me` | 로그인 | 내 정보 | P0 | 3 |
| A-07 | `PATCH /api/me` | 로그인 | 닉네임·학과 수정 | P1 | 3 |
| A-08 | `DELETE /api/me` | 로그인 | 탈퇴(익명화) | P2 | 3 |
| A-09 | `POST /api/auth/dev-login` | 공개 (`local` 프로필만) | 개발용 로그인 `{ "email" }` → 가입 여부에 따라 A-01과 같은 응답 | P0 | 2-A |

**A-01** 요청 `{ "idToken": "…" }` → 응답
```json
{ "registered": true,  "accessToken": "…", "user": {…} }
{ "registered": false, "signupToken": "…", "profile": { "emailMasked": "a***@mju.ac.kr" } }   // signupToken: 10분짜리 1회용. 가입 때만 쓴다
```
- 서버가 확인하는 것: Google 서명, 우리 앱의 클라이언트 ID(`aud`), 만료(`exp`), `email_verified=true`, **`hd` 값이 `schools.email_domain`과 같은가**. `hd`가 없거나 다르면 `AUTH_EMAIL_DOMAIN_NOT_ALLOWED`. (FE에서 Google 로그인 창에 `hd`를 힌트로 넘기는 것은 편의일 뿐이라 보안은 이 서버 검사가 맡는다)
- 사용자는 ID 토큰의 `sub`(`users.google_sub`)로 찾는다. 토큰이 위조·만료면 `AUTH_GOOGLE_TOKEN_INVALID`.
- 액세스 JWT는 서버가 발급한다(7일, 리프레시 없음). **Google 토큰은 이 호출에서만 쓰고 저장하지 않는다.** JWT가 만료되면 FE가 Google 로그인을 다시 거친다(보통 한 번 클릭).
- 사용량 제한: IP당 분당 10회 (`RATE_LIMITED`).
- `local` 프로필에는 Google 없이 개발할 수 있게 A-09(`dev-login`)만 열린다. 운영·시연 프로필에는 이 엔드포인트가 아예 등록되지 않는다.

**A-03** 요청 `{ "signupToken", "nickname", "departmentId", "agreedTerms": true }` → `201 { "accessToken", "user": {…} }`
- 닉네임 2~12자(한글·영문·숫자·`_`), 중복이면 `AUTH_NICKNAME_TAKEN`, 형식 오류 `AUTH_NICKNAME_INVALID`, 약관 미동의 `AUTH_TERMS_REQUIRED`, 토큰 오류 `AUTH_SIGNUP_TOKEN_INVALID`.

**A-04** `?nickname=` → `{ "available": true }`

**A-06** 응답 `{ "id", "nickname", "emailMasked": "a***@mju.ac.kr", "school": {"id","name"}, "department": {"id","name","collegeName"}, "role" }`

---

## 2. 과목·단원·제자 (`domain/course`, `domain/teaching`)

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| C-01 | `GET /api/courses` | 로그인 | 내 학교 과목 검색 (`keyword`,`departmentId`,`page`) | P0 | 4 |
| C-02 | `POST /api/courses` | 로그인 | 과목 만들기 | P0 | 4 |
| C-03 | `GET /api/me/courses` | 로그인 | 내 과목 = 제자가 있는 과목 | P0 | 4 |
| C-04 | `GET /api/courses/{courseId}` | 로그인 | 과목 상세 | P0 | 4 |
| C-05 | `GET /api/courses/{courseId}/units` | 로그인 | 주차(단원) 목록 + 내 현황 | P0 | 4 |
| C-06 | `POST /api/courses/{courseId}/units` | 로그인 | 주차(단원) 만들기 `{ "title": "3주차" }` | P0 | 4 |
| C-07 | `PATCH /api/units/{unitId}` | 로그인(만든 사람) | 주차 이름 수정 | P2 | 4 |
| D-01 | `POST /api/courses/{courseId}/disciple` | 로그인 | 제자 만들기 = 수강 등록 | P0 | 4 |
| D-02 | `GET /api/courses/{courseId}/disciple` | 로그인 | 내 제자 | P0 | 4 |
| D-03 | `PATCH /api/disciples/{id}` | 로그인(주인) | 제자 이름·캐릭터 수정 | P2 | 4 |

**C-02** `{ "name", "departmentId"?, "term": "2026-2" }` → `201`. 같은 학교·과목명(정규화)·학기가 있으면 `409 COURSE_DUPLICATE`에 **기존 과목 id를 함께** 돌려줘 FE가 그 과목으로 이동하게 한다.

**C-05** 응답 항목: `{ "id", "title", "materialCount", "myNoteCount", "battleReady": true }`
- `materialCount` = 이 주차에 연결된 `DONE` 자료 수 (M-06으로 연결).
- `myNoteCount` = 내 `ACTIVE` 노트 수. 0이면 이 주차로 방을 만들거나 들어갈 수 없다(`BATTLE_NOTES_REQUIRED`, 6절). FE는 이 값으로 방 만들기 버튼을 비활성화한다.
- `battleReady` = 이 주차에 배틀에 쓸 수 있는 문항이 충분한가 (기본 문항 수 5 기준, **주차 전체의 문항 수·내용은 내려주지 않는다**). 문항 수를 바꿔 보려면 Q-01을 쓴다(내가 아직 안 본 문항 수만 알려 준다).
- 주차는 **과목 안에서 공유**된다. 같은 이름(정규화)은 중복 생성되지 않는다 (`UNIT_DUPLICATE`, 기존 id 포함). 자료를 먼저 올리고 나서 주차를 고르거나 새로 만들어도 된다.

**D-01** `{ "name", "characterKey"? }` → `201`. 이미 있으면 `409 DISCIPLE_ALREADY_EXISTS`.

---

## 3. 강의자료 (`domain/material`)

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| M-01 | `POST /api/courses/{courseId}/materials/file` | 로그인 | PDF 업로드 (multipart) | P0 | 5 |
| M-02 | `POST /api/courses/{courseId}/materials/text` | 로그인 | 텍스트 직접 붙여넣기 | P0 | 5 |
| M-03 | `GET /api/materials/{id}` | 로그인(올린 사람) | 처리 상태 + 연결된 주차 조회 (폴링) | P0 | 5 |
| M-04 | `GET /api/courses/{courseId}/materials` | 로그인 | 내가 올린 자료 목록 (메타데이터 + 연결된 주차 id) | P0 | 5 |
| M-05 | `DELETE /api/materials/{id}` | 로그인(올린 사람) | 자료 삭제 → 근거로 쓰인 문항 `RETIRED` | P1 | 5 |
| M-06 | `PUT /api/materials/{id}/units` | 로그인(올린 사람) | 이 자료가 출처가 될 주차를 지정(전체 교체) `{ "unitIds": [3, 4] }` | P0 | 5 |

**자료는 과목에 올리고, 주차는 나중에 고른다**. 한 자료를 여러 주차에 연결할 수 있고, **자료 전체**가 연결된 모든 주차의 출처가 된다(쪽·문장 범위 지정 없음). 주차에 연결되지 않은 자료는 저장만 되고 문항을 만들지 않는다.

**M-01** multipart: `file`, `title`, `consentAgreed=true` → `202 { "materialId", "status": "PENDING" }`
- `consentAgreed`가 `true`가 아니면 `MATERIAL_CONSENT_REQUIRED` (서버에서도 검사). 동의문에는 "사용 권한이 있는 자료이고 개인정보를 제외했으며, **같은 과목 학생에게 근거 문장 일부가 보일 수 있다**"가 들어간다.
- PDF만, 20MB 이하. 확장자·MIME·매직 바이트 모두 확인. 위반 시 `MATERIAL_INVALID_FILE`, `MATERIAL_TOO_LARGE`.
- 추출 텍스트가 너무 적으면(스캔본) 처리 결과가 `FAILED`/`NO_TEXT`.
- 처리는 **비동기**(J-01). 끝나면 문장을 저장하고, **이미 연결된 주차마다** 개념 추출·문항 생성 작업(J-02)을 자동으로 건다.

**M-02** `{ "title", "text", "consentAgreed": true }` → `202`. 글자 수 상한(예: 100,000자) 초과 시 `MATERIAL_TOO_LARGE`.

**M-03** 응답 `{ "id", "title", "status", "failReason"?, "sentenceCount", "unitIds": [3, 4] }`

**M-04** 응답 항목 `{ "id", "title", "status", "createdAt", "unitIds": [3, 4] }`

**M-06** `{ "unitIds": [3, 4] }` → `200 { "materialId", "unitIds" }`. 빈 배열이면 연결을 모두 푼다.
- 모든 주차가 이 자료의 과목에 속해야 한다(`UNIT_NOT_IN_COURSE`). 자료를 올린 사람만 바꿀 수 있다. 자료가 `PROCESSING`이어도 연결할 수 있다.
- **새로 연결된 주차**: 자료가 이미 `DONE`이면 그 주차의 문항 생성 작업(J-02, `UNIT_MAPPED`)을 건다. `PROCESSING`이면 처리가 끝날 때 건다.
- **연결이 풀린 주차**: 그 주차에서 이 자료의 문장을 근거로 쓰던 문항을 `RETIRED`로 바꾼다 (M-05 자료 삭제와 같은 규칙).

문장 내용은 어떤 학생용 API로도 내려가지 않는다. (복기 화면의 근거 문장은 R-01이 **끝난 배틀의 문항**에 한해서, 학생이 정답 보기(R-11)를 누른 뒤에만 준다.)

---

## 4. 가르치기 (`domain/teaching`)

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| T-01 | `POST /api/disciples/{id}/messages` | 로그인(주인) | 학생 메시지 전송 → 제자 답 + 노트 변경 | P0 | 6 |
| T-02 | `GET /api/disciples/{id}/messages` | 로그인(주인) | 대화 내역 (`unitId`,`page`) | P0 | 6 |
| T-03 | `GET /api/disciples/{id}/notes` | 로그인(주인) | 현재 노트, **주차별로 묶어서** (`unitId` 선택) | P0 | 6 |
| T-04 | `GET /api/disciples/{id}/notes/{entryId}/history` | 로그인(주인) | 노트 수정·병합 이력(사슬) | P1 | 6 |
| T-05 | `POST /api/disciples/{id}/notes/merge` | 로그인(주인) | 확인 카드에서 학생이 "합치기"를 고름 `{ "keepEntryId", "mergeEntryId" }` | P1 | 6 |

**노트를 직접 수정·삭제하는 API는 만들지 않는다.** 수정과 삭제는 대화(T-01)로 하고, 합치기(T-05)는 내용을 바꾸지 않고 상태만 바꾼다.

**T-01** 요청
```json
{ "unitId": 3, "content": "스택은 나중에 넣은 것이 먼저 나오는 구조야.", "clientMessageId": "6f1c…(UUID)" }
```
응답 `201`
```json
{
  "turnNo": 4,
  "studentMessage": { "id": 101, "content": "…", "createdAt": "…" },
  "discipleMessage": { "id": 102, "content": "그럼 큐는 어떻게 달라요?", "askedWhy": false, "createdAt": "…" },
  "noteChanges": {
    "applied": [ { "op": "ADD", "entryId": 55, "content": "스택은 나중에 넣은 것이 먼저 나오는 구조야." } ],
    "merged": [ { "entryId": 40, "intoEntryId": 55 } ],
    "mergeSuggestions": [ { "newEntryId": 55, "existingEntryId": 31, "existingContent": "…", "similarity": 0.82 } ],
    "rejectedCount": 0,
    "noteLimitReached": false
  },
  "pasteWarning": false
}
```
- `applied`의 다른 형태: `{ "op": "UPDATE", "entryId": 56, "replacesEntryId": 12, "content": "…" }`, `{ "op": "REMOVE", "entryId": 12 }`.
- `merged`: 이번 턴에 **자동 병합**된 옛 노트(`entryId`)와 그것을 흡수한 새 노트(`intoEntryId`). 유사도 ≥ 0.9.
- `mergeSuggestions`: 유사도가 0.75 이상 0.9 미만이라 **학생에게 확인을 구할 쌍**. 두 노트 모두 아직 `ACTIVE`이고, 학생이 "합치기"를 누르면 FE가 T-05를 부른다. 서버는 제안을 저장하지 않으므로 무시하면 그대로 사라진다.
- `noteLimitReached`: 이 주차의 `ACTIVE` 노트가 상한(60개)이라 이번 턴의 `ADD`가 거부됐다는 뜻. FE가 "노트가 가득 찼어요. 기존 노트를 고치거나 지워 보세요"를 안내한다.
- `content` 1~2000자. `unitId`는 제자의 과목에 속한 주차(단원)여야 한다(`UNIT_NOT_IN_COURSE`).
- `clientMessageId`가 이미 처리된 값이면 **저장된 결과를 그대로 돌려준다**(중복 전송 방지).
- `askedWhy`: `turnNo`가 3의 배수면 서버가 제자에게 "왜/어떻게" 되묻도록 시킨다.
- `pasteWarning`: 학생 메시지가 이 주차에 연결된 자료 원문과 많이 겹치면 `true`. 경고만 하고 막지는 않는다. (입력 단계에는 지시문 검사가 없다 — 지시문 탐지는 채점 단계에서 한다.) FE는 이와 별개로 붙여넣는 순간 간단한 안내를 먼저 띄운다(서버 판정과 무관, 이 필드와 겹침 계산을 하지 않는다).
- 사용량 제한: 사용자당 분당 12회 (`RATE_LIMITED`). AI 장애 시 `AI_UNAVAILABLE`(503) / `AI_RATE_LIMITED`(429) / `AI_BAD_RESPONSE`(502) — **이 경우 아무것도 저장하지 않으므로** FE가 같은 `clientMessageId`로 재시도해도 된다.
- 동시에 두 번 보내 턴 번호가 겹치면 `TEACHING_CONCURRENT_TURN`(409).
- 처리 절차는 11-1.

**T-03** `?unitId=`는 선택. 응답은 **주차(단원)별로 묶은** 목록이다 — `ACTIVE`만.
```json
{ "groups": [
  { "unit": { "id": 3, "title": "3주차" }, "count": 12, "limit": 60,
    "items": [ { "id": 55, "content": "…", "createdAt": "…", "version": 1, "sourceMessageId": 101 } ] }
] }
```
- `unitId`를 주지 않으면 노트가 있는 주차만 `unit.id` 오름차순으로 돌려준다. `unitId`를 주면 노트가 없어도 그 주차 1개 그룹을 돌려준다.
- `count`/`limit`로 FE가 "12/60"을 보여 준다. 개념(`concepts`)별 묶음은 하지 않는다 — 개념 목록은 학생에게 내려가지 않는다.

**T-05** `{ "keepEntryId", "mergeEntryId" }` → `200 { "keepEntryId", "mergedEntryId" }`. 두 노트는 같은 제자·같은 주차의 `ACTIVE`여야 한다(`TEACHING_NOTE_MERGE_INVALID`). `mergeEntryId`는 `MERGED`가 되고 `merged_into_entry_id`가 `keepEntryId`를 가리킨다. 내용은 바뀌지 않는다. (확인 카드는 보통 새 노트를 `keepEntryId`로 채운다.)

---

## 5. 문제 은행 (`domain/question`)

학생 API는 하나뿐이다. 나머지는 관리자용(9절)과 내부 작업(10절)이다.

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| Q-01 | `GET /api/units/{unitId}/battle-readiness` | 로그인 | 이 단원으로 배틀할 수 있는가 | P0 | 7 |

`GET /api/units/{unitId}/battle-readiness?questionCount=5` (`questionCount` 3~10, 기본 5)

응답 `{ "ready": true, "availableForMe": 12, "required": 5 }` — **나에게 아직 안 나온 문항의 수**만 알려 준다 (내용 없음). `ready`는 `availableForMe ≥ questionCount`. 기준이 방에서 고르는 문항 수라서 `ready=true`인데 B-01에서 `BATTLE_QUESTION_BANK_NOT_READY`가 나는 일이 없다.

---

## 6. 배틀 (`domain/battle`)

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| B-01 | `POST /api/battles` | 로그인 | 방 만들기 | P0 | 8 |
| B-02 | `POST /api/battles/join` | 로그인 | 방 코드로 입장 | P0 | 8 |
| B-03 | `GET /api/battles/{id}` | 로그인(참가자) | 방·진행 상태 (폴링 2~3초) | P0 | 8 |
| B-04 | `POST /api/battles/{id}/ready` | 로그인(참가자) | 준비 완료 (누른 순간 내 노트가 스냅샷으로 저장됨) | P0 | 8 |
| B-05 | `POST /api/battles/{id}/cancel` | 로그인(참가자) | 방 취소·나가기(`ERROR`에서는 포기하고 나가기) — **참가자 누구나** 할 수 있다 | P0 | 8 |
| B-06 | `GET /api/battles` | 로그인 | 내 배틀 기록 (`mode`,`status`,`courseId`,`page`). `mode=PRACTICE`가 "연습 배틀 기록" | P0 | 8 |
| B-07 | `GET /api/battles/{id}/result` | 로그인(참가자) | 결과 (끝난 뒤) | P0 | 9 |
| B-08 | `PATCH /api/battles/{id}/participants/me` | 로그인(참가자) | 내 노트 공개 동의 변경 | P1 | 8 |
| B-09 | `POST /api/battles/{id}/solve/retry` | 로그인(참가자) | `ERROR` 배틀의 제자 풀이 재시도 (`ERROR → RUNNING`, 참가자 누구나) | P1 | 8 |
| B-10 | `POST /api/battles/practice` | 로그인 | **연습 배틀** 시작 (혼자, 랭킹 미반영. 랭킹 배틀의 복기에서 고른 답의 같은 문항을 다시 푼다) | P1 | 8 |

**B-01** `{ "courseId", "unitId", "questionCount": 5 }`(3~10) → `201 { "id", "roomCode": "K7M4QX", "status": "WAITING", "expiresAt" }`
- 이 API는 `RANKED` 방만 만든다. 연습은 B-10.
- 내 제자가 그 과목에 있어야 한다(`BATTLE_DISCIPLE_REQUIRED`). 같은 사용자의 진행 중 `RANKED` 방은 최대 1개(`BATTLE_ALREADY_ACTIVE`). 진행 중 = `WAITING`·`LOBBY`·`RUNNING`·`ERROR`.
- 내게 안 나온 문항이 부족하면 `BATTLE_QUESTION_BANK_NOT_READY` — 부족 사실을 알리고 저재고 작업(J-02, `LOW_STOCK`)을 건다.
- 그 주차에 내 `ACTIVE` 노트가 1개 이상이어야 한다(`BATTLE_NOTES_REQUIRED`, 409). FE는 C-05의 `myNoteCount`로 미리 비활성화한다. 연습 배틀(B-10)에는 이 규칙을 적용하지 않는다.

**B-02** `{ "roomCode" }` → `200` (B-03과 같은 형태). 오류: `BATTLE_ROOM_NOT_FOUND`, `BATTLE_ROOM_FULL`, `BATTLE_ROOM_EXPIRED`, `BATTLE_SELF_JOIN`, `BATTLE_DISCIPLE_REQUIRED`(입장자가 그 과목 제자가 없음 → FE가 D-01로 안내), `BATTLE_NOTES_REQUIRED`(입장자의 그 주차 `ACTIVE` 노트가 0개 → 오류 본문에 `unit`(id·title)과 `courseId`를 함께 줘서 FE가 가르치기로 안내).
- 코드는 대소문자·공백을 무시하고 먼저 `active_room_code`(`WAITING`/`LOBBY` 방만 가진다)로 찾는다. 없으면 `room_code`(방이 만료·취소·종료돼도 남는다)로 가장 최근 방을 찾아, 그 방이 `EXPIRED`면 `BATTLE_ROOM_EXPIRED`를 돌려준다(그 밖의 상태이거나 방이 없으면 `BATTLE_ROOM_NOT_FOUND`). 만료 처리(J-04)는 1분마다 돌므로, 찾은 방이 아직 `WAITING`/`LOBBY`여도 `expires_at`이 지났으면 만료로 보고 같은 코드를 준다. `room_code`에 인덱스를 둔다(erd).

**B-03** 응답
```json
{
  "id": 9, "status": "RUNNING",
  "mode": "RANKED", "course": {"id":1,"name":"자료구조"}, "unit": {"id":3,"title":"3주차"},
  "questionCount": 5, "roomCode": "K7M4QX", "expiresAt": "…",
  "participants": [
    { "userId": 1, "nickname": "나", "isMe": true,  "status": "READY", "solveStatus": "SOLVING" },
    { "userId": 2, "nickname": "친구", "isMe": false, "status": "READY", "solveStatus": "SOLVED" }
  ],
  "summary": null
}
```
- `status=FINISHED`이면 `summary`에 `{ "myScore", "opponentScore", "result": "WIN", "eloDelta": 12 }`. 연습 배틀은 `opponentScore`·`result`·`eloDelta`가 `null`이고 `participants`에 나만 있다.
- 응답에 `"mode": "RANKED" | "PRACTICE"`가 들어간다 (위 예시의 `mode`).
- **진행 중에는 문항·답·상대 노트를 일절 담지 않는다.**
- FE는 `FINISHED`·`CANCELED`·`EXPIRED`가 될 때까지 계속 조회한다. `ERROR`에서도 멈추지 않는다 — 다른 참가자의 재시도(`RUNNING`)나 포기(`CANCELED`)를 따라가야 한다.

**B-04** `{ "notesPublic": false }`(선택) → `204`. **멱등**(두 번 불러도 같은 결과). **누른 사람의 노트 스냅샷은 이 호출에서 바로 저장된다**(그 순간의 `ACTIVE` 노트 id를 `note_ids_json`에 기록). 이미 준비 상태이면 다시 불러도 스냅샷을 덮어쓰지 않는다(먼저 누른 순간의 노트가 유지된다). 그 주차에 `ACTIVE` 노트가 없으면 `BATTLE_NOTES_REQUIRED`(409). 두 사람이 모두 준비되면 서버가 (1) 문항 선택·고정, (2) `RUNNING` 전환, (3) 풀이 작업(J-03) 접수를 **한 번만** 수행한다. 문항이 부족하면(둘 다 안 본 문항이 `questionCount`보다 적음) 시작하지 않고 `LOBBY`에 머물며 두 사람의 준비와 스냅샷이 풀리고 `BATTLE_NOT_ENOUGH_QUESTIONS`가 나간다. 문항 수를 바꾸는 API는 없으므로, 이때는 방을 취소하고 문항 수를 줄여 다시 만든다.

**B-05** 방이 `WAITING`/`LOBBY`/`ERROR`일 때만. **참가자 누구나** 취소할 수 있고, 취소하면 방 전체가 `CANCELED`가 된다 (입장자가 나가기만 하고 방을 남기는 동작은 없다). `ERROR`에서의 취소는 "포기하고 나가기"이며 Elo는 바뀌지 않는다. `RUNNING`(풀이 중)과 `FINISHED`에서는 취소할 수 없다(`409`).

**B-07** 끝난 배틀에서만(`BATTLE_NOT_FINISHED`). 응답
```json
{
  "mode": "RANKED",
  "me": { "totalScore": 3.0, "correct": 3, "partial": 1, "wrong": 1, "unknown": 0 },
  "opponent": { "totalScore": 1.2, "correct": 2, "partial": 0, "wrong": 2, "unknown": 1 },
  "result": "WIN", "elo": { "before": 1000, "after": 1012, "delta": 12 },
  "questions": [
    { "seq": 1, "stem": "…", "myVerdict": "CORRECT", "myScore": 1, "opponentVerdict": "WRONG", "opponentScore": -0.5 }
  ]
}
```
- 점수는 소수다 (정답 +1, 부분 정답 +0.5, 오답 −0.5 또는 −0.3, 복붙 보정 후 소수 둘째 자리 반올림).
- 상대의 **답 원문은 두 사람이 모두 노트 공개에 동의한 경우에만** 포함한다.
- **연습 배틀**은 `"mode": "PRACTICE"`, `opponent`·`result`·`elo`가 `null`이고, 최상위에 `"sourceBattleId": 9`(원본 랭킹 배틀)가 붙는다 — FE가 "원래 배틀 복기로"와 정답 보기(R-11)에 쓴다. 문항마다 `"retestOf": { "battleAnswerId": 77, "verdict": "WRONG" }`(이 문항이 다시 시험하는 이전 답 — 같은 문항이다)이 붙는다. B-07은 모범 답과 근거를 싣지 않는다(정답은 원본 배틀의 R-01로 정답 보기 뒤에 본다).

**B-10** `{ "sourceBattleId": 9, "battleAnswerIds": [77, 80]? }` → `201 { "id", "mode": "PRACTICE", "status": "RUNNING", "questionCount": 2, "skippedAnswerIds": [] }`
- `sourceBattleId`는 **내가 참가한 끝난 `RANKED` 배틀**이어야 한다(`BATTLE_NOT_FINISHED`, 남의 배틀이면 403/404). **연습 배틀을 원본으로 삼을 수는 없다**(연쇄 금지 — `PRACTICE` 배틀을 주면 `INVALID_INPUT`). `battleAnswerIds`를 생략하면 그 배틀에서 내가 맞히지 못한 답(`WRONG`·`PARTIAL`·`UNKNOWN`·`UNJUDGED`) 전부, 주면 그 답들만 대상이다 — **맞힌 답(`CORRECT`)도 고를 수 있다**. 준 답이 그 배틀의 내 답이 아니면 403/404. (FE는 틀린 답을 미리 체크하고, 맞힌 답은 직접 고르게 한다.)
- 대상 답마다 **그 답의 문항을 그대로** 다시 낸다(새 문항을 뽑지 않는다. 개념과도 무관하다). 이미 `UNDER_REVIEW`·`RETIRED`가 된 문항의 답은 건너뛰고 `skippedAnswerIds`에 담는다. 쓸 수 있는 답이 하나도 없으면(대상이 없거나 전부 건너뜀) `PRACTICE_NOTHING_TO_REVIEW`. 문항은 원본 배틀의 문항 수(최대 10개)를 넘지 않는다. **정답 보기(R-11)를 이미 누른 배틀은 연습을 시작할 수 없다**(`PRACTICE_ANSWERS_REVEALED`, 409).
- 제자는 **지금의 노트**로 푼다 (복기 뒤 학생이 다시 가르쳤어야 의미가 있다). 생성 즉시 `RUNNING`이고 풀이(J-03)가 접수된다. 방 코드·상대·대기실이 없다. 노트가 0개여도 시작할 수 있다(`BATTLE_NOTES_REQUIRED`는 랭킹 방에만 적용).
- 채점은 랭킹 배틀과 같고 **Elo·전적·순위에 반영하지 않는다** (`result`는 NULL). 같은 문항은 원본 랭킹 배틀에서 이미 `user_question_history`에 기록돼 있으므로 **연습에서는 `user_question_history`에 쓰지 않는다**(`UNIQUE(user_id, question_id)`와도 겹친다).
- 사용량 제한은 B-01(방 만들기)과 함께 센다(10분 5회). 이미 `RUNNING`인 내 연습 배틀이 있으면 `BATTLE_ALREADY_ACTIVE`. 정답 보기 전에는 같은 원본 배틀로 몇 번이든 시작할 수 있다(이 사용량 제한만 적용).
- 결과는 B-03(폴링)·B-07로 본다. 정답·근거는 연습 결과에 싣지 않고, 원본 배틀의 R-01에서 정답 보기(R-11) 뒤에 본다.

배틀 상태 전이: `WAITING → LOBBY → RUNNING → FINISHED`, `WAITING/LOBBY → CANCELED/EXPIRED`, `RUNNING → ERROR`, **`ERROR → RUNNING`**(B-09로 풀이를 다시 시도할 때), **`ERROR → CANCELED`**(B-05로 참가자가 포기할 때). 연습 배틀은 생성과 동시에 `RUNNING`에서 시작한다. 허용되지 않은 전이는 `409 CONFLICT`.

---

## 7. 복기·리뷰·신고 (`domain/review`)

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| R-01 | `GET /api/battles/{id}/review` | 로그인(참가자) | 복기 화면 데이터 (정답·자료 근거는 정답 보기 전까지 숨김) | P0 | 11 |
| R-02 | `PUT /api/battle-answers/{answerId}/review` | 로그인(내 답) | 원인·메모 저장(있으면 수정) | P0 | 11 |
| R-03 | `POST /api/battle-answers/{answerId}/reports` | 로그인(내 답) | 문항 신고·채점 이의제기 | P0 | 11 |
| R-06 | `GET /api/battles/{id}/opponent-notes` | 로그인(참가자) | 상대 노트 (**양쪽 공개 동의 시**) | P1 | 11 |
| R-07 | `POST /api/battles/{id}/note-reviews` | 로그인(참가자) | 노트 교환 리뷰 작성 | P1 | 11 |
| R-08 | `GET /api/battles/{id}/note-reviews` | 로그인(참가자) | 내가 쓴 / 받은 리뷰 | P1 | 11 |
| R-09 | `PATCH /api/note-reviews/{id}` | 로그인(받은 사람) | 읽음·반영 표시 | P2 | 11 |
| R-10 | `GET /api/me/review-records` | 로그인 | 내 복기 기록 모아 보기 (`courseId`,`page`) | P1 | 11 |
| R-11 | `POST /api/battles/{id}/reveal-answers` | 로그인(참가자) | 정답 보기 — 이 배틀의 모범 답·자료 근거를 연다 (**되돌릴 수 없음**, 이 배틀의 연습이 끝난다) | P0 | 11 |

**R-01** 끝난 **`RANKED` 배틀에서만**(연습 배틀의 id를 주면 `INVALID_INPUT`). 응답은 `{ "answersRevealed": false, "items": [ … ] }`이고 항목은 문항마다 하나다. 항목에는 **내가 맞히지 못한 문항**(`WRONG`·`PARTIAL`·`UNKNOWN`·`UNJUDGED`)과 **채점 조작 의심으로 0점이 된 문항**(`ZEROED_INJECTION`, `injectionFlag=true`)이 들어가고, 연습 대상으로 고를 수 있게 **맞힌 문항**(`CORRECT`)도 `verdict`와 함께 들어간다(FE는 맞힌 문항을 접어서 보여 준다). **정답 보기(R-11) 전에는 `question.referenceAnswer`와 `evidence`를 싣지 않는다**(필드 자체를 뺀다). 항목
```json
{
  "battleAnswerId": 77, "seq": 2,
  "question": { "id": 31, "stem": "…" },
  "disciple": { "answerText": "…", "isUnknown": false, "verdict": "WRONG", "finalScore": -0.5,
                "copyFactor": 1.0, "injectionFlag": false },
  "usedNotes": [ { "id": 12, "content": "…" } ],
  "causeSuggestion": "NOTE_WRONG",
  "myReview": null,
  "practiceAvailable": true
}
```
정답 보기 뒤(`answersRevealed=true`)에는 항목에 `question.referenceAnswer`와 `"evidence": { "text": "…", "materialTitle": "3장 강의자료" }`가 더해진다.
- `practiceAvailable`: 이 답의 문항을 연습 배틀(B-10)에서 다시 풀 수 있는가. 문항이 `UNDER_REVIEW`·`RETIRED`이거나 `answersRevealed=true`이면 `false`.
- 정답 보기 전에는 **제자의 답 / 근거가 된 노트** 두 가지를, 본 뒤에는 **모범 답 / 강의자료 근거**까지 더해 세 가지를 나란히 보여 줄 수 있어야 한다.
- `causeSuggestion`은 **제안**일 뿐이다. 최종 원인은 R-02에서 학생이 고른다.
- 노트가 없는 경우 `usedNotes`는 빈 배열 → 원인 제안은 11-4의 규칙을 따른다(`NOT_TAUGHT` 또는 `NOT_IN_NOTES`).
- 맞힌 문항(`CORRECT`)은 원인 제안과 R-02가 필요 없다(`causeSuggestion`은 `null`). 연습 대상으로 고르는 용도다.
- `ZEROED_INJECTION` 항목에는 `injectionFlag=true`와 `verdict`만 내려가고, **어떤 패턴이 걸렸는지는 내려가지 않는다**(`grading_detail_json`의 패턴 목록은 학생에게 비공개). 학생은 R-03 `GRADING`으로 이의제기할 수 있다.

**R-02** `{ "userCause": "NOT_TAUGHT", "studyMemo", "aiMemo", "sourceNote", "reasonNote" }`(모두 선택, 원인만 필수) → `200`. 보완한 내용의 **출처와 고른 이유**(`sourceNote`,`reasonNote`)를 남기는 칸이 정보수집 역량의 증빙이 된다. `userCause`는 `NOT_TAUGHT`(가르치지 않음: 대화에서 말한 적 없음) / `NOT_IN_NOTES`(노트에 없음: 말했지만 노트로 안 남음) / `NOTE_WRONG`(노트가 틀림) / `NOT_APPLIED`(제자가 못 씀) / `OTHER`(기타) 중 하나다. 정답 보기 전에도 후에도 저장·수정할 수 있다.

**R-03** `{ "kind": "QUESTION" | "GRADING", "reasonType", "detail"? }` → `201`
- `QUESTION`이면 문항을 즉시 `UNDER_REVIEW`로 바꿔 이후 배틀에서 제외한다. 같은 답변에 같은 종류는 한 번만(`REPORT_DUPLICATE`).
- `GRADING`은 `ZEROED_INJECTION`으로 0점이 된 답에도 낼 수 있다. 정답 보기 전에도 후에도 낼 수 있다.

**R-11** 본문 없음 → `200 { "answersRevealed": true, "revealedAt": "…" }`. 내가 참가한 끝난 `RANKED` 배틀에서만(연습 배틀이면 `INVALID_INPUT`, 끝나지 않았으면 `BATTLE_NOT_FINISHED`).
- **되돌릴 수 없다.** 한 번 열면 그 배틀의 R-01이 `question.referenceAnswer`와 `evidence`를 함께 내려주고, **그 배틀을 원본으로 하는 연습 배틀(B-10)은 더 시작할 수 없다**(`PRACTICE_ANSWERS_REVEALED`). 이미 만든 연습 배틀의 진행·결과는 그대로다.
- **멱등**이다(이미 열었으면 같은 응답). 열기 전에는 같은 원본 배틀로 연습을 몇 번이든 시작할 수 있다(B-10의 사용량 제한만 적용).
- 열어도 원인 선택(R-02)·메모·신고(R-03)는 그대로 할 수 있다. 막히는 것은 연습뿐이다.
- 단위는 **배틀 전체**다(문항별로 열지 않는다). 내 참가 행(`battle_participants.answers_revealed_at`)에만 기록하고 상대에게는 영향이 없다.
- 연습 배틀 결과 화면에서도 부를 수 있다: B-07 응답의 `sourceBattleId`가 원본 배틀 id다.

> "처음 설명 vs 수정한 설명"은 원래 배틀과 연습 배틀의 노트 스냅샷(`note_ids_json`)을 비교해서 보여 준다 — B-07 연습 결과의 `retestOf`로 원래 답과 이어진다.

**R-06** 끝난 `RANKED` 배틀에서, **두 참가자가 모두 `notesPublic=true`일 때만**. 아니면 `REVIEW_NOTES_NOT_PUBLIC`(403). 각자 준비 완료를 누른 시점의 스냅샷(`note_ids_json`) 기준 노트를 돌려준다.

**R-07** `{ "goodPoints", "suggestions", "evidenceNoteEntryId"? }` → `201`. 배틀당 1회(`REVIEW_ALREADY_SUBMITTED`). 두 사람이 모두 공개 동의한 배틀에서, 상대의 공개된 노트만 대상으로 쓸 수 있다(아니면 `REVIEW_NOTES_NOT_PUBLIC`).

---

## 8. 랭킹 (`domain/ranking`)

| ID | 메서드·경로 | 인증 | 설명 | P | WP |
|---|---|---|---|---|---|
| K-01 | `GET /api/rankings` | 로그인 | 과목별 순위 (`courseId`,`scope`,`departmentId`,`page`) | P0 | 10 |
| K-02 | `GET /api/me/ratings` | 로그인 | 내 과목별 Elo | P0 | 10 |
| K-03 | `GET /api/me/records` | 로그인 | 친구(상대)별 전적 (`courseId`) | P0 | 10 |
| K-04 | `GET /api/me/records/{opponentId}/battles` | 로그인 | 특정 상대와의 배틀 목록 | P1 | 10 |
| K-05 | `GET /api/rankings/growth` | 로그인 | 성장폭 랭킹 (`courseId`,`days`=21) | P1 | 10 |
| K-06 | `GET /api/rankings/departments` | 로그인 | 학과·단과대 대항전 (상위 N명 평균) | P2 | 10 |
| K-07 | `GET /api/me/ratings/history` | 로그인 | 내 Elo 변동 이력 (`courseId`,`page`) — 랭킹 화면의 "점수 변화" | P1 | 10 |

**랭킹·전적·성장폭·Elo 이력에는 `RANKED` 배틀만 센다.** 연습 배틀 기록은 B-06(`mode=PRACTICE`)에서 본다.

**K-07** 응답 항목 `{ "battleId", "opponent": {"id","nickname"}, "ratingBefore", "ratingAfter", "delta", "createdAt" }`, 최신순. `elo_histories`를 그대로 읽는다.

**K-01** `scope`: `SCHOOL`(기본) / `DEPARTMENT`. 응답 `{ "items": [ { "rank", "userId", "nickname", "departmentName", "rating", "gamesPlayed", "wins", "losses", "draws" } ], "me": { … } , "page", … }`. `games_played ≥ 1`만. 동점이면 `user_id`가 작은 쪽이 앞.

**K-03** 응답 `[ { "opponent": {"id","nickname"}, "wins", "losses", "draws", "lastBattleAt" } ]`

**K-05** 응답은 K-01과 같은 모양에 `delta`(기간 내 Elo 증감) 기준 정렬. 순위뿐 아니라 "많이 성장한 학생"이 보상받게 한다.

---

## 9. 관리자·운영 (`/api/admin/**`, 관리자만)

관리자는 `.env`의 `ADMIN_EMAILS`에 적힌 **Google 계정 이메일**로 가입한 사용자다 (가입 때 `role=ADMIN`). **P1이지만 시험 사용(11/13~)에는 반드시 필요하다.** 시간이 없으면 SQL로 대체한다.

| ID | 메서드·경로 | 설명 | P |
|---|---|---|---|
| X-01 | `POST /api/admin/units/{unitId}/question-jobs` | 문항 생성 작업 수동 실행 `{ "targetCount" }` → `202 { jobId }` | P1 |
| X-02 | `GET /api/admin/question-jobs/{jobId}` | 작업 진행·단계별 탈락 수 | P1 |
| X-03 | `GET /api/admin/questions` | 문항 목록 (`unitId`,`status`; `status=UNJUDGED`로 검수 대기 문항만 거른다) + 사용 횟수·정답률 | P1 |
| X-04 | `GET /api/admin/questions/{id}` | 문항 상세 + 검증 로그 | P1 |
| X-05 | `PATCH /api/admin/questions/{id}/status` | 상태 변경 (`VERIFIED`↔`UNDER_REVIEW`↔`RETIRED`; **`UNJUDGED` → `VERIFIED`**(사람이 검수해 통과)와 **`UNJUDGED` → `RETIRED`**도 가능) | P1 |
| X-06 | `POST /api/admin/questions` | 문항 직접 추가 (시연 세트 검수용, `source=MANUAL`) | P1 |
| X-07 | `GET /api/admin/reports` | 신고·이의제기 목록 (`status`) | P1 |
| X-08 | `PATCH /api/admin/reports/{id}` | 처리 `{ "status", "resolutionNote", "questionAction": "RETIRE"\|"RESTORE"\|"NONE" }` | P1 |
| X-09 | `GET /api/admin/stats/questions` | 정답률 0%·100%에 가까운 문항 | P1 |
| X-10 | `GET /api/admin/stats/ai-calls` | 일·용도별 AI 호출 수 (무료 티어 한도 감시) | P1 |
| X-11 | `GET /api/admin/stats/usage` | 시험 사용 지표 (재방문, 연습 배틀 후 정답 비율, 오진단 건수 등) | P2 |
| X-12 | `GET /api/admin/exports/battle-answers.csv` | 채점 일치도 비교용 내보내기 | P2 |

---

## 10. 내부 작업 (API 아님)

| ID | 작업 | 실행 시점 | 비고 |
|---|---|---|---|
| J-01 | 자료 처리: 텍스트 추출 → 문장 분리·정규화 → 저장 → `DONE` | M-01/02 직후, 비동기 | 끝나면 **연결된 주차마다** J-02를 건다 |
| J-02 | 문항 생성 작업: 개념 추출 → 생성 → 4단계 검증 → 저장 | 자료 처리 완료(`MATERIAL_DONE`) / 이미 `DONE`인 자료를 주차에 연결(`UNIT_MAPPED`, M-06) / 재고 부족 / 관리자 요청 | 아래 11-2. 주차 단위로 돈다 |
| J-03 | 배틀 풀이·채점: 제자 풀이(참가자당 AI 1회) → 채점 → 결과 → Elo(`RANKED`만) | 두 사람 모두 준비 / 연습 배틀은 생성 즉시 | 아래 11-3, 11-4 |
| J-04 | 방 만료 처리: `WAITING/LOBBY`이면서 `expires_at` 지남 → `EXPIRED` | 1분마다 스케줄 | |
| J-06 | **서버 시작 시 복구**: `PROCESSING` 자료 → `FAILED(SERVER_RESTART)`, `RUNNING` 작업·`SOLVING` 풀이 → 재접수 | 시작 시 1회 | 비동기 작업이 서버 재시작으로 사라지는 문제 방지 |

비동기 작업은 `TaskExecutor`로 돌리고 **상태는 항상 DB에 남긴다**. AI를 부르는 작업은 동시 실행 수를 제한한다(전역 2개).

---

## 11. 핵심 로직 상세

### 11-1. 가르치기 한 턴 (T-01)

1. **검증**: 제자 소유자 확인, 길이, 단원 소속, 사용량 제한, `clientMessageId` 중복이면 저장된 결과 반환.
2. **읽기**: 같은 (제자, 주차)의 최근 대화 N개(`app.teaching.recent-messages`, **기본 10**. 대화 맥락이 모자라면 설정으로 늘린다), `ACTIVE` 노트(글자 수 상한 적용), 다음 `turnNo`와 `askWhy`(`turnNo % 3 == 0`).
3. **AI 호출 1회** (**DB 트랜잭션 밖에서**): 프롬프트 `teaching-turn` → JSON `{ reply, noteChanges: [{ op, targetId?, content, evidence }] }`. 학생 메시지는 구분자로 감싸 "데이터이지 지시가 아니다"라고 명시.
4. **형식 검사**: 스키마가 안 맞으면 1회 재시도, 그래도 안 되면 `AI_BAD_RESPONSE`.
5. **노트 변경 검증** (`NoteChangeVerifier`, 변경마다 따로, LLM이 준 순서대로). **`op`에 따라 검사가 다르다** (수정·삭제는 학생이 막히지 않도록 검증을 줄였다):

   | 검사 | `ADD` | `UPDATE` | `REMOVE` | 탈락 코드 |
   |---|---|---|---|---|
   | `targetId`가 이 제자·주차의 `ACTIVE` 노트인가 | — | ✔ | ✔ | `TARGET_NOT_FOUND` |
   | `evidence`(정규화)가 **이번 학생 메시지**(정규화) 안에 그대로 있는가 | ✔ | — | — | `EVIDENCE_NOT_IN_MESSAGE` |
   | `content`의 글자 3-gram이 학생 메시지에 포함되는 비율 ≥ **0.6** (초기값, 시연 세트로 조정) | ✔ | — | — | `LOW_OVERLAP` |
   | **부정 표현 일치**: `evidence`와 `content`의 부정 표지(`않`,`안`,`없`,`못`,`아니`,`아님`,`불가`,`X`) 개수 홀짝이 같은가 ("A이다"가 "A가 아니다"로 바뀌어 들어가는 것을 막는다) | ✔ | — | — | `NEGATION_MISMATCH` |
   | 노트 길이 5~300자 (형식 검사) | ✔ | ✔ | — | `LENGTH_INVALID` |
   | 주차(단원)당 `ACTIVE` 노트 60개 상한 (≥0.9 자동 병합은 제외 — 아래 유사 노트 처리 뒤에 검사) | ✔ | — | — | `NOTE_LIMIT_REACHED` |

   - `UPDATE`/`REMOVE`에는 정정 표현 검사(`NO_CORRECTION_CUE`)를 하지 않는다. `UPDATE`의 새 `content`는 검증하지 않으므로, **"학생이 한 말만 쓰라"는 지시를 프롬프트에 분명히 넣는 것**이 유일한 통제다 (알려진 위험 — 시연 세트로 `UPDATE`가 학생이 말하지 않은 내용을 만들어 내는지 점검한다).
   - **유사 노트 처리** (`ADD`가 위 검사를 통과한 뒤, 같은 제자·주차의 `ACTIVE` 노트와 글자 3-gram 유사도 비교):
     - 유사도 ≥ **0.9** → **자동 병합**: 새 노트를 `ACTIVE`로 저장하고, 비슷한 옛 노트들은 `MERGED`(`merged_into_entry_id` = 새 노트)로 바꾼다. 최신 것만 활성으로 남는다. 노트 수가 늘지 않으므로 상한 검사 대상이 아니다.
     - 0.75 ≤ 유사도 < 0.9 → 새 노트를 `ACTIVE`로 저장하되 응답의 `mergeSuggestions`에 담는다(학생 확인 카드). 합치기는 T-05. 노트가 한 개 늘어나므로 **상한을 검사한다** — 가득 차 있으면 `NOTE_LIMIT_REACHED`로 거부하고 제안도 만들지 않는다.
     - 그 외 → 그냥 `ADD`. 이것도 상한을 검사한다.
     - 임계값은 설정(`app.teaching.merge-auto-threshold` 0.9, `merge-suggest-threshold` 0.75).
     - 정리하면 순서는 **유사도 비교 → ≥0.9 자동 병합(개수 불변, 상한 제외) → 나머지 `ADD`는 모두 상한 검사**다. 상한 값은 60이다. 병합이 아닌 `ADD`는 0.75~0.9 구간도 검사하므로 61번째 노트가 저장되는 일이 없다.
   - 중복은 탈락이 아니므로 `rejected_note_changes`에 남기지 않는다. 검사에 걸린 변경만 그 코드와 함께 남긴다.
   - **상한에 걸려도 대화는 계속된다.** T-01 자체는 성공하고(응답 `noteLimitReached=true`), `ADD`만 거부된다. 학생은 같은 대화로 `UPDATE`/`REMOVE`를 해서 자리를 만들 수 있다 (노트를 고치는 길은 T-01뿐이므로 상한이 대화를 막으면 안 된다).
6. **저장** (짧은 트랜잭션): 학생 메시지 → 제자 메시지 → 통과한 변경(`ADD`=새 행, 자동 병합이면 옛 행 `MERGED`, `UPDATE`=새 행+옛 행 `SUPERSEDED`, `REMOVE`=옛 행 `REMOVED`) → 탈락 변경(`rejected_note_changes`) → `ai_call_logs`. `UNIQUE(disciple,unit,turn_no,role)` 위반이면 `TEACHING_CONCURRENT_TURN`.
7. **붙여넣기 경고**: 학생 메시지와 이 주차에 연결된 자료 원문의 겹침 비율이 높으면 `pasteWarning=true`.

제자의 **답변(`reply`) 속 누출**(가르치지 않은 지식)은 코드로 완전히 막을 수 없다. 프롬프트로 최소화하고 문항 쪽 누출 시험(11-2의 3단계)이 안전망이다.

### 11-2. 문제 은행 파이프라인 (J-02)

| 단계 | 하는 일 | 탈락 조건 |
|---|---|---|
| 0. 개념 추출 | 그 주차에 **연결된 모든 자료**의 문장(문장 번호 포함)에서 개념 목록을 뽑아 `concepts` 저장. AI 1회/주차 | 근거 문장 번호가 자료에 없으면 그 개념 제외 |
| 1. 생성 | 개념 최대 5개씩 묶어 AI 1회. 문항 `{conceptLabel, stem, gradingMode, answers/keyElements, referenceAnswer, evidenceQuote, evidenceSentenceSeqs}` | JSON 형식 오류 |
| 2. 코드 확인 | `evidenceQuote`가 인용한 문장에 실제로 있는가(정규화 후 포함 또는 유사도 ≥0.9). 정답(`answers` 중 하나, 또는 핵심 요소의 80% 이상)이 근거 문장 안에서 성립하는가. 문항 길이·중복(기존 문항과 유사도 ≥0.8) | `EVIDENCE_NOT_FOUND`, `ANSWER_NOT_IN_EVIDENCE`, `DUPLICATE` |
| 3. 누출 시험 | **노트가 빈 제자**에게 후보 전체를 한 번에 풀게 한다(AI 1회). 코드 채점기로 채점해 **맞히면 폐기**("가르치지 않아도 풀리는 문항") | 정답 맞힘 |
| 4. 외부 판정 | 문항을 만든 모델이 아닌 다른 모델(`JudgeClient`)에 "이 문항과 정답은 강의자료와 일치한다"를 묻고 일치율 ≥ **0.8**만 통과 | 0.8 미만 |

- 작업은 **주차 단위**다. 그 주차에 `DONE`인 연결 자료가 하나도 없으면 시작하지 않는다.
- 통과하면 `questions` + `question_evidences` 저장. 4단계까지 통과 → `VERIFIED`, **4단계를 아직 못 쓰는 동안**(Jev 보류)에는 1~3단계 통과 문항을 `UNJUDGED`로 저장한다. 배틀은 `app.question.allow-unjudged` 설정이 `true`일 때만 `UNJUDGED`를 쓴다(개발·시연은 `true`). 시연 직전에 `false`로 바꿔 검수한 `VERIFIED` 문항만 쓰게 할 수도 있다. 그때를 위해 관리자가 X-03(`status=UNJUDGED`)으로 검수 대기 문항을 모아 보고 X-05로 `VERIFIED`(통과) 또는 `RETIRED`로 정리한다.
- 단계마다 `question_validation_logs`에 `candidate_json`을 남겨 **어느 단계에서 몇 개 탈락했는지** 집계한다.
- 목표 개수(`targetCount`)를 채울 때까지 최대 3라운드. 한 작업의 AI 호출은 (개념 수/5)+1+1회 수준이다.

### 11-3. 배틀 시작과 제자 풀이 (J-03)

1. **시작 준비** (B-04, 배틀 행을 `SELECT … FOR UPDATE`로 잠근 트랜잭션에서):
   - **각자 `READY`를 누르는 순간**: 그 참가자의 `ACTIVE` 노트 id를 `note_ids_json`에 스냅샷한다. 노트가 0개면 `BATTLE_NOTES_REQUIRED`로 거부한다. 이미 `READY`면 건드리지 않는다(멱등 — 먼저 누른 순간의 노트가 유지된다).
   - **두 사람 모두 `READY`가 되는 순간** (같은 트랜잭션):
     - 문항 선택: 해당 주차의 `VERIFIED`(+허용 시 `UNJUDGED`) 중 **두 사람 모두 `user_question_history`에 없는** 것. 후보를 `id` 순으로 정렬한 뒤 **`battles.id`를 시드로 한 셔플**로 고른다(같은 후보면 같은 세트). 개념이 겹치지 않게 먼저 고르고, 모자라면 겹침 허용. `questionCount`보다 적으면 `BATTLE_NOT_ENOUGH_QUESTIONS` — 시작하지 않고 `LOBBY`에 머물며 두 사람의 `READY`와 스냅샷을 비운다(다시 누를 때 새로 찍는다).
     - `battle_questions` 고정, `user_question_history` 기록, `RUNNING`.
   - **연습 배틀(B-10)**: 대기실이 없고 참가자가 1명이다. 생성 요청 안에서 (1) 지금의 `ACTIVE` 노트 스냅샷(0개여도 된다), (2) 대상 답마다 **그 답의 문항을 그대로** `battle_questions`에 넣고(`source_answer_id` 기록 — 새 문항을 뽑지 않으므로 시드·개념 선택이 없다), (3) `RUNNING`으로 만든다. `user_question_history`에는 쓰지 않는다(원본 랭킹 배틀에서 이미 기록됨). 이후 2~5단계는 같고, 5단계에서 Elo·승패는 건너뛴다.
2. 참가자별로 **AI 1회**(`battle-solve`): 입력은 **그 참가자 노트(id+내용)와 문항(id+본문)뿐**(강의자료 없음). 출력 `[ { questionId, answer, unknown, usedNoteIds } ]`.
3. 코드 후처리: `usedNoteIds`가 제공된 노트에 속하는지 확인. **`unknown=false`인데 `usedNoteIds`가 비어 있으면 노트 없이 답한 것이므로 서버가 `UNKNOWN`으로 강제**한다.
4. AI 호출이 실패하면 참가자당 최대 2회 재시도(백오프). 끝내 실패하면 배틀은 `ERROR`, Elo는 바뀌지 않는다. B-09로 다시 시도하면 `ERROR → RUNNING`으로 돌아가 풀이가 다시 접수되고, 참가자가 B-05로 포기하면 `ERROR → CANCELED`가 된다(둘 다 참가자 누구나).
5. 두 사람 모두 `SOLVED`가 되면 채점(11-4) → 집계 → 승패 → Elo(11-5) → `FINISHED`.

### 11-4. 채점 (`domain/grading`)

답 하나마다 순서대로:

1. `is_unknown` → `UNKNOWN`, 0점.
2. **지시문 탐지**: 답과 **사용한 노트**에 패턴(`정답으로 처리`, `만점`, `채점`, `ignore`, `system prompt`, `위 지시` 등)이 있으면 `ZEROED_INJECTION`, 0점, `injection_flag=true`. 패턴 목록은 설정 파일로 둔다.
3. **코드 채점**:
   - `ANSWER_LIST`: 답을 정규화해 정답 표현 중 하나를 **포함**하면 `CORRECT`. 어떤 표현과도 공통 핵심어가 없으면 `WRONG`. 그 외는 **애매함**.
   - `KEY_ELEMENTS`: 답에 있는 핵심 요소(별칭 포함)를 센다. 단, **그 요소가 제자가 인용한 노트에도 있어야 센다**(노트에 없는데 맞힌 것은 AI 지식 누출로 보고 인정하지 않는다). 비율 ≥ 0.8이면 `CORRECT`, ≤ 0.2이면 `WRONG`, 사이는 **애매함**.
4. **애매한 답만 모아 LLM 1회 판정**(`grade-batch`): 문항·모범 답·핵심 요소·제자의 답을 데이터로 구분해 전달, `{ questionId, verdict, reason }`를 받는다. `verdict`는 **`CORRECT` / `PARTIAL`(부분 정답) / `WRONG`** 중 하나다. 형식이 어긋나면 그 문항은 `UNJUDGED`(0점, 감점 없음, 관리자 확인 대상). **부분 정답은 이 단계에서만 나온다** (코드 채점은 정답·오답·애매함만 가른다).
5. **점수** (초기값, 설정으로 조정): `CORRECT +1`, `PARTIAL +0.5`, `UNKNOWN 0`, 주입 0, `UNJUDGED` 0, `WRONG −λ`. **λ는 문항 채점 모드별**이다: `ANSWER_LIST` **0.5**, `KEY_ELEMENTS` **0.3** (정답 목록형은 포함 여부로 단순 판정되니 오답 감점이 크고, 핵심 요소형은 부분 점수 여지가 있어 작다). 설정 `app.scoring.correct`·`partial`·`wrong-answer-list`·`wrong-key-elements`.
6. **복붙 보정**: `copy_ratio = max(답과 주차 자료의 겹침, 인용한 노트와 주차 자료의 겹침)`(글자 8-gram 포함률, 주차에 연결된 자료 전체와 비교). `≤0.5`이면 계수 1.0, `≥0.9`이면 **0.5**, 사이는 선형. **`CORRECT`와 `PARTIAL`일 때만** `final_score = raw_score × copy_factor` (감점에는 곱하지 않는다). 소수 둘째 자리에서 반올림. (제안서는 "답"만 보지만, 붙여넣기는 노트에 들어가므로 노트도 함께 본다)
7. **원인 제안**(`WRONG`·`PARTIAL`·`UNKNOWN`·`UNJUDGED`일 때) — 그 참가자의 준비 완료 시점 노트 스냅샷 기준. **제안일 뿐이고 최종 원인은 학생이 고른다(R-02).** 원인은 5가지다 — `NOT_TAUGHT`(가르치지 않음: 대화에서 말한 적 없음), `NOT_IN_NOTES`(노트에 없음: 말했지만 노트로 안 남음), `NOTE_WRONG`(노트가 틀림), `NOT_APPLIED`(제자가 못 씀), `OTHER`(기타 — 학생만 고른다):
   - 근거 문장과 겹치는 노트가 하나도 없음 → 이 제자·이 주차에서 학생이 보낸 메시지와 근거 문장을 같은 3-gram 유틸로 비교한다. 겹치는 메시지가 있으면(말했는데 노트로 안 남음) `NOT_IN_NOTES`, 없으면 `NOT_TAUGHT`. (시간이 모자라면 이 비교를 빼고 `NOT_IN_NOTES` 하나만 제안한다.)
   - 겹치는 노트는 있으나 핵심 요소가 빠졌거나 부정 표현이 근거와 반대 → `NOTE_WRONG`
   - 핵심 요소가 노트에 모두 있는데도 틀림·모름 → `NOT_APPLIED` (AI 쪽 문제일 수 있음)
8. 근거(`grading_detail_json`)를 저장해 복기 화면에 보여 준다.

**알려진 한계**: `ANSWER_LIST`는 정답 표현을 *포함*하면 `CORRECT`이므로, 정답 후보를 줄줄이 *나열*한 답을 오답으로 거르는 장치는 없다 (흐름도의 "나열은 오답" 가드는 도입하지 않기로 했다). 시연 세트 점검에서 이런 답이 나오는지 본다.

정규화·n-gram·부정 표현 검출은 **하나의 유틸**(`global/text` 또는 `domain/material`의 공용 클래스)을 `teaching`·`question`·`grading`이 함께 쓴다. 단위 테스트를 가장 먼저 붙인다.

### 11-5. Elo (`domain/ranking`)

- **`RANKED` 배틀에만 적용한다.** 연습 배틀(`PRACTICE`)은 승패·Elo·전적을 만들지 않는다.
- 승패: 합계 점수(소수 둘째 자리 기준)가 높은 쪽 승, 같으면 무승부. (둘 다 노트가 0개인 경우는 B-01·B-02·B-04의 "노트 1개 이상" 규칙 때문에 생기지 않는다.)
- 기대 승률 `E_A = 1 / (1 + 10^((R_B − R_A)/400))`, 갱신 `R_A' = round(R_A + K·(S_A − E_A))`, `K=32`, 승 1·무 0.5·패 0, 초기 1000.
- 두 `elo_ratings` 행을 **`user_id` 순으로** `FOR UPDATE`로 잠가 데드락을 피한다(없으면 1000으로 생성).
- `elo_histories`와 `elo_ratings`를 **한 트랜잭션**에서 반영. `UNIQUE(battle_id,user_id)` 위반 = 이미 반영됨 → 건너뛴다(멱등).

---

## 12. 오류 코드 카탈로그

`ErrorCode` enum에 한 곳으로 모은다. **이름은 한 번 정하면 바꾸지 않는다**(FE가 문자열로 분기). HTTP 상태는 `ErrorCode`가 가진다.

| 영역 | 코드 (HTTP) |
|---|---|
| 공통 | `INVALID_INPUT`(400) `UNAUTHORIZED`(401) `FORBIDDEN`(403) `NOT_FOUND`(404) `CONFLICT`(409) `RATE_LIMITED`(429) `INTERNAL_ERROR`(500) |
| AI | `AI_RATE_LIMITED`(429) `AI_UNAVAILABLE`(503) `AI_TIMEOUT`(504) `AI_BAD_RESPONSE`(502) |
| 인증 | `AUTH_GOOGLE_TOKEN_INVALID`(401) `AUTH_EMAIL_DOMAIN_NOT_ALLOWED`(403) `AUTH_SIGNUP_TOKEN_INVALID`(400) `AUTH_NICKNAME_TAKEN`(409) `AUTH_NICKNAME_INVALID`(400) `AUTH_TERMS_REQUIRED`(400) |
| 과목·제자 | `COURSE_DUPLICATE`(409) `UNIT_DUPLICATE`(409) `UNIT_NOT_IN_COURSE`(400) `DISCIPLE_ALREADY_EXISTS`(409) `DISCIPLE_NOT_FOUND`(404) |
| 자료 | `MATERIAL_CONSENT_REQUIRED`(400) `MATERIAL_INVALID_FILE`(400) `MATERIAL_TOO_LARGE`(413) |
| 가르치기 | `TEACHING_CONCURRENT_TURN`(409) `TEACHING_NOTE_MERGE_INVALID`(409) |
| 배틀 | `BATTLE_ROOM_NOT_FOUND`(404) `BATTLE_ROOM_FULL`(409) `BATTLE_ROOM_EXPIRED`(410) `BATTLE_SELF_JOIN`(400) `BATTLE_ALREADY_ACTIVE`(409) `BATTLE_DISCIPLE_REQUIRED`(409) `BATTLE_QUESTION_BANK_NOT_READY`(409) `BATTLE_NOT_ENOUGH_QUESTIONS`(409) `BATTLE_NOT_FINISHED`(409) `BATTLE_NOTES_REQUIRED`(409) `PRACTICE_NOTHING_TO_REVIEW`(409) `PRACTICE_ANSWERS_REVEALED`(409) |
| 복기·신고 | `REVIEW_NOTES_NOT_PUBLIC`(403) `REVIEW_ALREADY_SUBMITTED`(409) `REPORT_DUPLICATE`(409) |

`BATTLE_NOTES_REQUIRED`는 입장(B-02)에서 오류 본문에 `unit`(`id`,`title`)과 `courseId`를 추가로 담는다.

**HTTP 오류가 아닌 탈락 사유**: 노트 변경 검증(11-1)에서 걸린 변경은 T-01을 실패시키지 않고 `rejected_note_changes.failed_checks_json`에 `EVIDENCE_NOT_IN_MESSAGE` `LOW_OVERLAP` `NEGATION_MISMATCH` `LENGTH_INVALID` `NOTE_LIMIT_REACHED` `TARGET_NOT_FOUND` 중 하나로 남고, T-01 응답에는 `rejectedCount`(상한이면 `noteLimitReached`)로만 나간다.

`global/error/ErrorCode`에는 `RATE_LIMITED`(429)와 `AI_*`처럼 현재 없는 상태 코드가 필요하다.
