# domain/teaching — 가르치기와 제자 노트  ★ 핵심 도메인

## 책임
학생이 대화로 제자를 가르치고, 학생이 한 말이 **제자 노트**에 쌓인다.

## 규칙
1. **한 턴에 LLM을 한 번만** 호출한다. 응답 하나에 `reply`(제자의 말)와 `noteChanges`(노트 변경 목록)를 함께 받는다.
2. `noteChanges`는 **그대로 반영하지 않는다.** 변경마다 서버 코드가 학생의 메시지와 대조해 통과한 것만 반영한다.
   - 근거 구절(`evidence`)이 학생 메시지에 실제로 들어 있는가
   - 노트 내용과 학생 메시지가 글자 단위로 얼마나 겹치는가 (기준값은 설정으로 분리)
   - 부정 표현(않/안/없/못/아니 …)이 일치하는가 — "A이다"가 "A가 아니다"로 바뀌어 들어가는 것을 막는다
3. 검증에서 탈락한 변경은 버리되 `rejected_note_change`에 이유와 함께 남긴다. (디버깅·발표 자료)
4. **노트는 읽기 전용이다.** 노트를 직접 수정·삭제하는 API를 만들지 않는다. 고치려면 대화로 다시 말해야 한다.
5. 노트 문장은 **학생이 쓴 표현 그대로** 저장한다. LLM이 말을 다듬거나 지식을 덧붙이게 하지 않는다.
6. 제자는 3메시지마다 "왜 그런가요?", "어떻게 하나요?"처럼 되묻는다. 횟수는 서버가 센다 (모델에게 맡기지 않는다).
7. 제자는 학생이 가르친 것만 안다. 프롬프트로 "모르는 척"을 시켜도 지식이 샐 수 있으므로, **노트가 빈 제자로 누출 시험**을 하는 코드는 `question`과 이 도메인이 공유한다.
8. 노트 검증 로직은 순수 클래스로 만들고, 통과·탈락 사례를 단위 테스트로 충분히 쌓는다. (예: 근거 없는 변경, 부정 뒤집기, 학생이 말하지 않은 개념 추가)

## 구조 (6-A까지 구현)
- `logic/` — DB·AI를 모르는 순수 클래스. `NoteChangeVerifier`(op별 검증, 자동 병합·확인 카드 제안, 상한 검사)와 그 입출력 값(`ProposedNoteChange`, `ActiveNote`, `AcceptedChange`, `RejectedChange`, `VerificationResult`, `NoteCheckFailure`).
- `service/TeachingTurnEngine` — 한 턴에 AI를 한 번 부르고 검증기를 돌린다. DB를 읽거나 쓰지 않는다. 호출하는 서비스(6-B)는 이 엔진을 **트랜잭션 밖에서** 부르고, 돌려받은 `TurnOutcome`을 짧은 트랜잭션에서 저장한다.
- `config/TeachingProperties` — `app.teaching.*` 기준값. 숫자를 코드에 흩뿌리지 않는다.
- 프롬프트는 `resources/prompts/teaching-turn.v1.txt`. 바꾸려면 `v2`를 새로 만들고 `TeachingTurnEngine.PROMPT_VERSION`을 올린다.
- `NoteCheckFailure`의 이름은 `rejected_note_changes.failed_checks_json`에 그대로 저장되므로 바꾸지 않는다.
