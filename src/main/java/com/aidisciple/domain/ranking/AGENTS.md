# domain/ranking — Elo·전적·순위

## 책임
과목별 Elo, 친구 전적, 과목별 순위. (학과·단과대 대항전 K-06은 여유가 있을 때만 — P2)
설계: [api-spec.md](../../../../../../../docs/api-spec.md) 8절·11-5와 그 아래 "구현 메모(WP10)".

## 규칙
- Elo(초기값 1000, K=32는 계획서의 초기 가정이다. 바꾸면 문서도 같이 고친다)는 **DB를 모르는 순수 계산 클래스**(`logic/EloCalculator`)로 만들고 단위 테스트한다.
- 배틀 결과가 확정될 때 **한 트랜잭션에서 한 번만** 반영한다. 같은 배틀을 두 번 반영하지 않는다 (멱등성). 이미 반영됐는지 미리 확인하고, `UNIQUE(battle_id, user_id)`가 마지막 보루다.
- **`RANKED` 배틀만** 반영하고 센다. 연습 배틀은 Elo·전적·순위를 만들지 않는다.
- 순위 집계에는 인증(학교 메일)을 마친 학생만 포함한다. 과목은 **같은 학교 것만** 볼 수 있다(없는 과목과 다른 학교 과목은 같은 `NOT_FOUND`).
- 사용자·과목·학과 정보를 데이터에 포함해 두어 대항전·타 학교 확장이 가능하게 한다.
- 순위 조회는 페이지네이션을 쓴다. 전체 목록을 한 번에 내려주지 않는다. 동점이면 `user_id`가 작은 쪽이 앞이다.

## 구조
- `logic/EloCalculator` — 기대 승률·갱신·승패 점수. 숫자는 여기 한 곳에만 있다.
- `entity/` — `EloRating`(사용자·과목별 레이팅과 전적), `EloHistory`(배틀별 변동, 추가 전용).
- `service/EloService` — 끝난 랭킹 배틀의 Elo 를 반영한다. **두 레이팅 행을 항상 `user_id` 순으로 `FOR UPDATE`** 로 잠그고(없으면 1000점으로 만든다), 이력과 레이팅을 한 트랜잭션에서 바꾼다.
- `service/EloBattleListener` — battle 의 `BattleFinishedEvent` 를 **평범한 `@EventListener`** 로 받는다. 채점 정산과 같은 트랜잭션이라 "`FINISHED`이면 Elo 도 반영돼 있다". (`@TransactionalEventListener(AFTER_COMMIT)` 으로 바꾸지 않는다 — 결과 화면이 Elo 변화 없이 뜨는 순간이 생긴다.)
- `service/EloRecovery` — 서버가 시작될 때 Elo 가 빠진 끝난 랭킹 배틀을 끝난 순서대로 반영한다.
- `service/EloChangeLookup` — battle 이 정한 `BattleEloLookup` 을 구현한다(배틀 응답의 Elo 변화).
- `service/RankingQueryService` — K-01~K-05·K-07 조회.

## 지킬 것
- 이 도메인은 **배틀의 테이블을 직접 만지지 않는다.** 결과는 `BattleRecordService`로만 읽는다. 사용자·학과는 `UserLookupService`·`DepartmentService`, 과목 이름은 `CourseService` 로 읽는다.
- battle 은 랭킹을 모른다. 의존 방향은 `ranking → battle` 한쪽뿐이고, 배틀 응답의 Elo 는 battle 의 인터페이스를 랭킹이 구현해 채운다.
- 학생에게 상대의 노트·답을 내리지 않는다 — 전적·순위는 점수와 승패만 담는다.
