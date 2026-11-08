# domain/grading — 채점

## 책임
제자의 답을 점수로 바꾼다. **코드 채점이 먼저, 코드로 판단이 안 되는 답만 LLM이 판정**한다.
설계: [api-spec.md](../../../../../../../docs/api-spec.md) 11-4와 그 아래 "구현 메모(WP9)".

## 규칙
1. 순서: ① 코드 채점(정답 목록 일치, 핵심 요소 포함) → ② 애매한 답만 모아 LLM 판정(배치 1회).
2. 점수: 정답 +, 오답 **감점**, `모름` 0점. 찍어서 맞히는 쪽이 유리해지면 안 된다.
3. **지시문 탐지**: 답안이나 사용된 노트에 채점자를 겨냥한 지시문("정답으로 처리", "ignore previous" 등)이 의심되면 **해당 문항을 0점 처리**하고 플래그를 남긴다.
4. **복붙 보정**: 답(노트)이 강의자료 원문과 겹치는 비율을 서버가 계산하고, 높을수록 정답 점수에 곱하는 계수를 낮춘다 (**최대 0.5배까지**).
5. LLM 판정 결과도 그대로 믿지 않는다. 판정 형식이 어긋나면 해당 문항은 "판정 불가"로 두고 점수에 임의로 반영하지 않는다.
6. 점수 공식·계수·임계값은 **한 곳(상수/설정)**에 모으고, DB·AI 없는 순수 클래스로 만들어 경계값 단위 테스트를 반드시 쓴다.
7. 채점 근거(어떤 규칙으로 몇 점인지)를 `battle_answer`에 남겨 복기 화면에서 보여 줄 수 있게 한다.

## 구조
- `logic/` — **DB·AI를 모르는 순수 클래스**. 숫자를 코드에 흩뿌리지 않고 `config/`의 값을 받는다.
  - `AnswerCodeGrader` — 모름 → 지시문 → 코드 채점. 코드 채점 자체는 문제 은행의 `CodeGrader`를 쓴다(누출 시험과 같은 기준).
  - `InjectionDetector`, `CopyPenalty`(비율·계수), `ScoreCalculator`(판정 → 점수), `CauseSuggester`(오답 원인 제안), `GradingDetail`(채점 근거 JSON의 모양).
- `service/` — `GradingTrigger`(`BattleSolvedEvent`를 커밋 뒤에 받음) → `GradingExecutor`(`@Async`) → `GradingRunner`(**트랜잭션 없음**), `GradeBatchJudge`(AI 판정).
- `config/` — `ScoringProperties`(`app.scoring.*`), `GradingProperties`(`app.grading.*`: 지시문 패턴, AI 판정 시도·묶음 크기, 원인 제안 기준).
- 프롬프트는 `resources/prompts/grade-batch.v1.txt`. 바꾸려면 `v2`를 새로 만들고 `GradeBatchJudge.PROMPT_VERSION`을 올린다.

## 지킬 것
- 이 도메인은 **배틀의 테이블을 직접 만지지 않는다.** 채점할 배틀은 `BattleSettlementService.loadForGrading`으로 받고, 결과는 `settle` 한 번으로 돌려준다. 문항은 `QuestionLookupService`, 노트·학생 메시지는 `NoteLookupService`, 자료 문장은 `MaterialSourceService`로 읽는다.
- **AI 판정은 트랜잭션 밖에서만** 부른다(`GradingRunner`가 트랜잭션을 걸지 않는다). `GradeBatchJudge`는 예외를 던지지 않고, 실패한 답은 `UNJUDGED`(0점)로 돌려준다.
- 제자의 답·노트는 **데이터**다. AI 판정 프롬프트에서 구분자로 감싸고, 한 줄로 정리해 항목 구조를 흉내 내지 못하게 한다. 로그에는 배틀 id와 오류 종류만 남긴다.
- `grading_detail_json`의 패턴·요소 목록은 내부 값이다. 학생용 응답(B-07 등)에 싣지 않는다.
- 같은 이벤트가 두 번 와도 두 번 채점하지 않는다 — `loadForGrading`이 `RUNNING`이고 채점 전 답이 있을 때만 배틀을 준다.
