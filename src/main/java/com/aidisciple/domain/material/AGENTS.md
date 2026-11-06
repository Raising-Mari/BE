# domain/material — 강의자료 업로드·텍스트 추출

## 책임
PDF·텍스트 업로드 → 텍스트 추출 → **문장 단위로 쪼개 저장**(`material_sentences`) → 자료가 출처가 될 주차 연결.
문제 은행의 "근거 문장 확인"과 복붙 보정이 이 문장들을 기준으로 삼는다.
설계: [api-spec.md](../../../../../../../docs/api-spec.md) 3절, 테이블: [erd.md](../../../../../../../docs/erd.md) 6-2.

## 흐름과 구조
접수(M-01·M-02, 동기) → 처리(J-01, 비동기) → 조회(M-03·M-04) / 주차 연결(M-06)

- `service/MaterialIntakeService` — 접수. **트랜잭션을 걸지 않는다.** 자료 행(PENDING)을 먼저 커밋해야 곧바로 시작되는 처리 스레드가 그 행을 볼 수 있다. 검사 → 저장 → 처리 작업을 `taskExecutor` 큐에 넣고 202로 답한다. 큐가 가득 차면 접수를 취소(`DELETED`)하고 `RATE_LIMITED`.
- `service/MaterialProcessor` → `MaterialProcessingService` — 처리. **트랜잭션 밖에서** 글을 뽑고, DB 쓰기는 `MaterialStateService`의 짧은 트랜잭션들(`markProcessing`·`complete`·`fail`)로 한다. PDF를 읽는 동안 DB 커넥션을 쥐지 않기 위해서다.
- `service/MaterialStateService` — 자료 상태를 바꾸는 유일한 곳. 상태는 PENDING → PROCESSING → DONE/FAILED로만 흐른다.
- `service/MaterialQueryService`(M-03·M-04), `MaterialUnitService`(M-06)
- `extract/PdfTextExtractor` — **PDFBox는 이 클래스 밖으로 나가지 않는다.**
- `logic/` — DB를 모르는 순수 클래스: `UploadValidator`(확장자·MIME·매직 바이트·크기), `SentencePreparer`(문장 분리·정규화·해시), `TextSanitizer`(제어 문자·깨진 서러게이트 제거)
- `repository/MaterialSentenceWriter` — 문장 저장은 건수가 많아 JDBC 배치로 한다(JPA는 IDENTITY 키라 한 줄씩 넣는다). `MaterialSentence` 엔티티는 읽기와 스키마 검증용.
- `event/` — 다른 도메인에 알리는 이벤트(아래)

## 규칙
- 업로드 전 "본인이 사용 권한을 가진 자료만, 개인정보 제외" 동의를 **서버에서도 검사**하고(`MATERIAL_CONSENT_REQUIRED`) 시각을 기록한다.
- **원본 파일은 저장하지 않는다.** 접수 때 받은 내용을 메모리로 처리 작업에 넘기고 끝나면 버린다. 그래서 서버가 도중에 재시작되면 그 자료는 다시 처리할 수 없고, 시작할 때 `MaterialRecovery`가 `FAILED/SERVER_RESTART`로 닫는다(사용자가 다시 올린다).
- 자료는 **올린 사람만** 본다. 남의 자료·삭제된 자료·없는 자료는 모두 `NOT_FOUND`다. 문장 내용은 어떤 학생용 응답에도 담지 않는다.
- 문장 분리·정규화·해시는 `global/text`의 것을 그대로 쓴다. 가르치기 검증·근거 문장 확인·복붙 보정과 같은 값이어야 서로 찾을 수 있다. 해시는 `TextNormalizer.normalize`한 문자열의 SHA-256이다.
- 추출한 글·파일 내용·파일 이름을 로그와 예외 메시지에 남기지 않는다.
- 문장은 쪽 경계를 넘지 않고, 줄바꿈은 항상 문장 경계다. 슬라이드 자료에 맞춘 규칙이라, 한 문단이 여러 줄로 접힌 문서 PDF는 문장이 줄 단위로 잘릴 수 있다.
- 자료 처리 결과가 `FAILED`가 되는 이유: `NO_TEXT`(글이 거의 없음 = 스캔본), `TOO_LARGE`(쪽수·글자 수 초과), `PARSE_ERROR`(암호·깨진 파일·예기치 않은 오류), `SERVER_RESTART`. 기준값은 `app.material.*`.
- 녹음본(음성) 업로드는 후순위(P2)다.

## 문제 은행(WP7)이 이어받을 것
이 도메인은 문제 은행을 모른다. 대신 이벤트를 낸다. 둘 다 **트랜잭션 안에서 발행되므로** 받는 쪽은 `@TransactionalEventListener(AFTER_COMMIT)`를 쓴다.
- `MaterialProcessedEvent` — 처리가 끝나 문장이 저장됐다. 이미 연결된 주차마다 문항 생성 작업(J-02, `MATERIAL_DONE`)을 건다.
- `MaterialUnitsChangedEvent` — M-06으로 연결이 바뀌었다. 새로 연결된 주차는 (자료가 DONE이면) `UNIT_MAPPED` 작업을, 풀린 주차는 이 자료의 문장을 근거로 쓰던 문항을 `RETIRED`로 바꾼다.
- **자료 삭제(M-05)는 아직 없다.** 근거로 쓰인 문항을 `RETIRED`로 바꾸는 일이 핵심이라 문제 은행과 함께 만든다. 문장을 지울 때는 `question_evidences`(FK)를 먼저 정리해야 한다.
