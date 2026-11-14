# domain/battle — 배틀(방·준비·진행)

## 책임
친구와 방을 만들어 같은 문제 세트를 각자의 제자가 푼다. **비동기 + 폴링** 방식이다 (웹소켓 없음, 실시간 대결은 제외 범위).
혼자 하는 **연습 배틀**(B-10)도 여기 있다. 설계: [api-spec.md](../../../../../../../docs/api-spec.md) 6절·11-3, 테이블: [erd.md](../../../../../../../docs/erd.md).

## 구조
- `entity/` — `Battle`(상태 전이 메서드가 허용되지 않은 전이를 `CONFLICT`로 막는다), `BattleParticipant`(준비·풀이 상태), `BattleQuestion`, `BattleAnswer`.
- `service/` — 방 안에서의 생애와 풀이는 역할별로 나뉜다. **`...Store`는 짧은 트랜잭션, 바깥 문(`...Service`)은 트랜잭션이 없다.**
  - `BattleRoomService` — 방 만들기(B-01)·입장(B-02)·취소(B-05)·공개 동의(B-08). 사용자 행 → 배틀 행 순으로 잠근다.
  - `BattleReadyService` → `BattleStartStore` — 준비 완료(B-04)·풀이 재시도(B-09). 커밋된 **뒤에** 풀이를 접수하고, 문항 부족 오류도 커밋 뒤에 던진다.
  - `BattlePracticeService` → `BattlePracticeStore` — 연습 배틀(B-10).
  - `BattleSolveDispatcher` → `BattleSolveExecutor`(`@Async`) → `BattleSolveRunner` → `BattleSolveStore` — 제자 풀이(J-03). **AI는 Runner에서, 트랜잭션 밖에서만** 부른다.
  - `BattleSolveRecovery` — 서버가 시작될 때 풀던 참가자를 다시 접수(J-06).
  - `BattleExpiryJob` → `BattleExpiryStore` — 입장 시간이 지난 방을 `EXPIRED`로(J-04, 1분마다).
  - `BattleQueryService` — B-03·B-06과 응답 조립.
  - `BattleResultService` — B-07 결과(끝난 배틀, 참가자만. 모범 답·근거·채점 근거는 싣지 않는다).
  - `BattleRecordService` — **랭킹(ranking 도메인)이 끝난 랭킹 배틀의 결과를 읽는 유일한 창구**(`finishedRanked`, `rankedRecordsOf`, `opponentIds`, `finishedRankedBattleIds`). `BattleEloLookup` — 배틀 응답에 싣는 Elo 변화를 읽는 인터페이스(구현은 랭킹이 한다. battle 은 랭킹을 모른다).
  - `BattleSettlementService` — **채점(grading 도메인)이 배틀을 읽고 쓰는 유일한 창구.** `loadForGrading`(RUNNING·모두 풀이 완료·채점 전 답이 있을 때만), `settle`(한 트랜잭션: 답 판정·점수, 참가자 합계·건수·승패, `FINISHED`, `BattleFinishedEvent`).
- `logic/` — `RoomCodes`(6자리 코드, 헷갈리는 글자 제외), `NoteIdsJson`.
- `event/BattleSolvedEvent` — 모두 풀어 채점할 차례가 됐다(`grading`의 `GradingTrigger`가 받는다). `event/BattleFinishedEvent` — 채점이 끝나 `FINISHED`가 됐다(랭킹이 `RANKED`일 때 같은 트랜잭션에서 Elo 를 반영한다).
- `config/BattleProperties` — `app.battle.*` (방 유효 시간, 풀이 재시도).

## 규칙
- 방은 6자리 코드로 입장한다. 같은 방에 중복 입장하거나 자기 자신과 대결할 수 없다. 사용자당 진행 중인 `RANKED` 방은 하나다.
- 상태는 enum으로 관리하고(`WAITING → LOBBY → RUNNING → FINISHED`, 그 외 `CANCELED`·`EXPIRED`·`ERROR`) **상태 전이는 `Battle`의 메서드로만** 한다. 허용되지 않은 전이는 `CONFLICT`.
- **상태를 바꾸는 모든 요청은 `battles` 행을 `FOR UPDATE`로 먼저 잠근다**(`findLockedById`). "둘 다 준비되면 한 번만 시작", "둘 다 풀면 한 번만 채점"이 이 잠금에 기댄다. 같은 사람의 요청은 `UserLookupService.lockActive`로 먼저 줄 세운다.
- **잠금은 읽기 순서까지 지켜야 한다.** MySQL 기본 격리(REPEATABLE READ)에서는 트랜잭션의 첫 일반 읽기가 스냅샷을 고정한다. 배틀 행 잠금보다 먼저 참가자를 일반 읽기로 읽으면, 잠금을 기다리는 사이 다른 참가자가 커밋한 값을 못 본다. **다른 참가자의 상태로 판단할 때**(예: 둘 다 풀었는가)는 `findLockedByBattleId`(잠금 읽기, 항상 배틀 행을 잠근 뒤에 id 순)로 읽는다. 자기 행만 보고 판단하는 곳은 일반 읽기여도 된다. (2026-11-09, `BattleSolveStore.complete` 에서 두 참가자가 동시에 끝내면 채점 이벤트가 빠지던 결함)
- 문항 세트는 시작 시점에 뽑아 `battle_questions`로 **고정**한다. 두 참가자는 같은 세트를 푼다. 노트 스냅샷은 **각자 준비를 누른 순간** 찍고 다시 쓰지 않는다. 풀이는 항상 그 스냅샷의 노트로 한다.
- 제자는 **강의자료가 아니라 자기 노트만** 보고 푼다. 노트에 없으면 `모름`이다 — `DiscipleSolver`가 노트 인용이 없는 답을 `모름`으로 강제한다. 노트가 0개면 AI를 부르지 않는다.
- 한 판의 LLM 호출은 **참가자당 1회**(전 문항 일괄). 실패하면 최대 3회 시도하고, 끝내 실패하면 배틀이 `ERROR`다(Elo 불변). `ERROR`는 참가자 누구나 재시도(B-09)하거나 포기(B-05)한다.
- 풀이 결과의 답은 `battle_answers`에 `verdict=PENDING`·0점으로 저장한다. **점수·판정은 `grading`이 계산하고, Elo는 `ranking`이 반영한다.** 이 도메인은 직접 계산하지 않고 받은 결과를 `settle`로 반영만 한다.
- **진행 중에는 문항·답·상대 노트를 응답에 담지 않는다.** 없는 배틀과 남의 배틀은 같은 `NOT_FOUND`다(`{id}` 경로는 참가자인지 서버가 검사한다).
- 풀이 접수는 **커밋 뒤에** 한다(`...Service` 바깥 문). 서버가 그 사이에 꺼져도 `BattleSolveRecovery`가 `RUNNING` 배틀의 `PENDING`·`SOLVING` 참가자를 다시 접수한다.
- 연습 배틀은 원본 랭킹 배틀의 답에서 **같은 문항**을 다시 낸다. `user_question_history`·Elo·전적을 건드리지 않는다.
- 같은 요청이 두 번 들어와도(새로고침, 재시도) 결과가 중복 생성되지 않게 한다 — 준비·취소·입장은 멱등이다.
- 로그에 노트·답 내용을 남기지 않는다. 실패 이유에는 오류 코드나 예외 클래스 이름만 쓴다.

## 채점·랭킹과의 연결
- 풀이가 끝나면 `BattleSolvedEvent` → `grading`이 채점 → `BattleSettlementService.settle` → `FINISHED` + `BattleFinishedEvent`. 채점이 실패하면 배틀은 `RUNNING`(두 참가자 `SOLVED`)으로 남고 서버가 다시 시작될 때 `BattleSolveRecovery`가 `BattleSolvedEvent`를 다시 낸다.
- **랭킹과의 연결**: `settle`이 `FINISHED`로 만드는 같은 트랜잭션에서 `BattleFinishedEvent`를 내고, 랭킹이 `@EventListener`로 받아 `RANKED`일 때만 Elo 를 반영한다. 그래서 `FINISHED`이면 Elo 도 항상 반영돼 있고(`BattleResponse.Summary.eloDelta`·`BattleResultResponse.elo` 가 채워진다), 반영이 실패하면 정산이 되돌려져 배틀은 `RUNNING` 으로 남는다. 랭킹 도메인은 `BattleRecordService` 로만 결과를 읽는다.
