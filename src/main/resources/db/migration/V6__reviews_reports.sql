-- 복기, 노트 교환 리뷰, 신고·이의제기 (docs/erd.md 6-6)
-- 연습 배틀은 별도 테이블 없이 battles(mode=PRACTICE)·battle_questions(source_answer_id)를 그대로 쓴다.

CREATE TABLE review_entries (
	id               BIGINT      NOT NULL AUTO_INCREMENT,
	battle_answer_id BIGINT      NOT NULL,
	user_id          BIGINT      NOT NULL,
	user_cause       VARCHAR(20) NOT NULL,
	study_memo       TEXT        NULL,
	ai_memo          TEXT        NULL,
	source_note      TEXT        NULL,
	reason_note      TEXT        NULL,
	created_at       DATETIME(6) NOT NULL,
	updated_at       DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_review_entries_battle_answer UNIQUE (battle_answer_id),
	INDEX idx_review_entries_user (user_id),
	CONSTRAINT fk_review_entries_battle_answer FOREIGN KEY (battle_answer_id) REFERENCES battle_answers (id),
	CONSTRAINT fk_review_entries_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 두 참가자가 모두 notes_public=true 일 때만 쓰고 읽을 수 있다 (서버가 검사). RANKED 배틀만 대상이다.
CREATE TABLE note_reviews (
	id                     BIGINT      NOT NULL AUTO_INCREMENT,
	battle_id              BIGINT      NOT NULL,
	reviewer_id            BIGINT      NOT NULL,
	target_user_id         BIGINT      NOT NULL,
	good_points            TEXT        NOT NULL,
	suggestions            TEXT        NOT NULL,
	evidence_note_entry_id BIGINT      NULL,
	read_at                DATETIME(6) NULL,
	applied_at             DATETIME(6) NULL,
	created_at             DATETIME(6) NOT NULL,
	updated_at             DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_note_reviews_battle_reviewer UNIQUE (battle_id, reviewer_id),
	INDEX idx_note_reviews_target (target_user_id),
	CONSTRAINT fk_note_reviews_battle FOREIGN KEY (battle_id) REFERENCES battles (id),
	CONSTRAINT fk_note_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
	CONSTRAINT fk_note_reviews_target FOREIGN KEY (target_user_id) REFERENCES users (id),
	CONSTRAINT fk_note_reviews_evidence_note FOREIGN KEY (evidence_note_entry_id) REFERENCES note_entries (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE reports (
	id               BIGINT      NOT NULL AUTO_INCREMENT,
	reporter_id      BIGINT      NOT NULL,
	kind             VARCHAR(10) NOT NULL,
	question_id      BIGINT      NOT NULL,
	battle_answer_id BIGINT      NULL,
	reason_type      VARCHAR(20) NOT NULL,
	detail           TEXT        NULL,
	status           VARCHAR(15) NOT NULL DEFAULT 'OPEN',
	resolution_note  TEXT        NULL,
	handled_by       BIGINT      NULL,
	handled_at       DATETIME(6) NULL,
	created_at       DATETIME(6) NOT NULL,
	updated_at       DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	-- battle_answer_id 가 NULL 인 문항 신고는 UNIQUE 에서 중복으로 보지 않는다. 같은 문항 중복 신고는 서버가 막는다.
	CONSTRAINT uk_reports_reporter_answer_kind UNIQUE (reporter_id, battle_answer_id, kind),
	INDEX idx_reports_status_created (status, created_at),
	INDEX idx_reports_question (question_id),
	CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
	CONSTRAINT fk_reports_question FOREIGN KEY (question_id) REFERENCES questions (id),
	CONSTRAINT fk_reports_battle_answer FOREIGN KEY (battle_answer_id) REFERENCES battle_answers (id),
	CONSTRAINT fk_reports_handled_by FOREIGN KEY (handled_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
