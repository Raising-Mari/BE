# 운영 SQL 절차

API로 만들지 않고 SQL로 처리하기로 한 운영 작업을 모은다. **운영 DB에서 실행하기 전에 꼭 백업하고, 0단계(확인)부터 순서대로** 한다.
모든 시각은 UTC로 저장되어 있다(`UTC_TIMESTAMP(6)`).

## M-05 자료 삭제 (팀 결정 2026-11-09: API 대신 SQL 절차)

자료를 지우면 그 자료의 문장을 근거로 쓴 문항은 더 이상 배틀에 나오면 안 된다(api-spec M-05: 근거로 쓰인 문항 `RETIRED`).

> ⚠️ `question_evidences.sentence_id`는 `material_sentences`를 **삭제 연쇄 없이** 참조한다. 근거로 쓰인 자료는 `DELETE FROM materials`가 외래키 오류로 실패한다. 그래서 절차를 둘로 나눈다.

| 절차 | 언제 | 결과 |
|---|---|---|
| **A. 사용 중지 (권장)** | 보통 | 근거 문항 `RETIRED` + 주차 연결 해제. 자료·문장 행은 남아 복기·신고 처리에서 근거를 계속 볼 수 있다 |
| B. 완전 삭제 | 저작권 요청처럼 원문 문장을 꼭 지워야 할 때 | A + 근거 연결(`question_evidences`) 삭제 + 자료 삭제(문장은 자동 삭제). 이 자료를 근거로 썼던 문항은 근거 연결이 사라진다(문항의 `evidence_text`는 남는다) |

### 0. 확인 (읽기만 한다)

```sql
SET @material_id = 0; -- 지울 자료 id 로 바꾼다

SELECT id, course_id, title, status, sentence_count FROM materials WHERE id = @material_id;

-- 연결된 주차
SELECT mu.unit_id, u.title FROM material_units mu JOIN units u ON u.id = mu.unit_id WHERE mu.material_id = @material_id;

-- 이 자료의 문장을 근거로 쓴, 아직 폐기되지 않은 문항
SELECT q.id, q.unit_id, q.status, LEFT(q.stem, 60) AS stem
FROM questions q
WHERE q.status <> 'RETIRED'
  AND q.id IN (SELECT qe.question_id
               FROM question_evidences qe JOIN material_sentences s ON s.id = qe.sentence_id
               WHERE s.material_id = @material_id);

-- 이 주차에 대기·실행 중인 문항 생성 작업 (있으면 끝날 때까지 기다린다)
SELECT j.id, j.unit_id, j.status FROM question_generation_jobs j
WHERE j.status IN ('QUEUED', 'RUNNING')
  AND j.unit_id IN (SELECT unit_id FROM material_units WHERE material_id = @material_id);
```

### A. 사용 중지 (권장)

```sql
START TRANSACTION;

-- 1) 근거로 쓴 문항을 폐기한다 (이후 배틀·연습에서 빠진다)
UPDATE questions
SET status = 'RETIRED', updated_at = UTC_TIMESTAMP(6)
WHERE status <> 'RETIRED'
  AND id IN (SELECT qe.question_id
             FROM question_evidences qe JOIN material_sentences s ON s.id = qe.sentence_id
             WHERE s.material_id = @material_id);

-- 2) 주차 연결을 끊는다 (이 자료로 새 문항이 생기지 않는다)
DELETE FROM material_units WHERE material_id = @material_id;

-- 결과가 0단계에서 본 건수와 맞는지 확인한 뒤 COMMIT, 아니면 ROLLBACK
COMMIT;
```

### B. 완전 삭제

A를 먼저 한 뒤에 이어서 한다.

```sql
START TRANSACTION;

-- 3) 근거 연결을 지운다 (외래키 때문에 자료보다 먼저)
DELETE qe FROM question_evidences qe
JOIN material_sentences s ON s.id = qe.sentence_id
WHERE s.material_id = @material_id;

-- 4) 자료를 지운다. material_sentences·material_units 는 ON DELETE CASCADE 로 함께 지워진다
DELETE FROM materials WHERE id = @material_id;

COMMIT;
```

### 확인

```sql
SELECT COUNT(*) FROM material_units WHERE material_id = @material_id;      -- 0
SELECT COUNT(*) FROM questions q
WHERE q.status <> 'RETIRED'
  AND q.id IN (SELECT qe.question_id FROM question_evidences qe
               JOIN material_sentences s ON s.id = qe.sentence_id
               WHERE s.material_id = @material_id);                          -- 0
```

관리자 화면에서는 X-03(`status=RETIRED`)으로 폐기된 문항을 확인할 수 있다.
