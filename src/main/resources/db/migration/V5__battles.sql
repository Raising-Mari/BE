-- 배틀: 방, 참가자, 고정된 문항 세트, 제자의 답과 채점 결과 (docs/erd.md 6-5)

CREATE TABLE battles (
	id               BIGINT       NOT NULL AUTO_INCREMENT,
	course_id        BIGINT       NOT NULL,
	unit_id          BIGINT       NOT NULL,
	mode             VARCHAR(10)  NOT NULL DEFAULT 'RANKED',
	-- PRACTICE 일 때 어느 RANKED 배틀의 복기에서 시작했는가. 연습을 원본으로 삼는 연쇄는 서버가 막는다.
	source_battle_id BIGINT       NULL,
	-- 방이 만료·취소·종료돼도 값을 남긴다 (만료된 코드를 BATTLE_ROOM_EXPIRED 로 알려 주기 위해). 그래서 UNIQUE 가 아니다.
	room_code        CHAR(6)      NULL,
	-- WAITING·LOBBY 일 때만 room_code 와 같은 값, 아니면 NULL. NULL 은 UNIQUE 에서 중복으로 보지 않으므로 진행 중인 방끼리만 코드가 겹치지 않는다.
	active_room_code CHAR(6)      NULL,
	status           VARCHAR(20)  NOT NULL,
	question_count   TINYINT      NOT NULL DEFAULT 5,
	created_by       BIGINT       NOT NULL,
	expires_at       DATETIME(6)  NULL,
	started_at       DATETIME(6)  NULL,
	finished_at      DATETIME(6)  NULL,
	error_reason     VARCHAR(100) NULL,
	created_at       DATETIME(6)  NOT NULL,
	updated_at       DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_battles_active_room_code UNIQUE (active_room_code),
	INDEX idx_battles_created_by_status (created_by, status),
	INDEX idx_battles_status_expires_at (status, expires_at),
	INDEX idx_battles_created_by_mode_created_at (created_by, mode, created_at),
	INDEX idx_battles_room_code (room_code),
	INDEX idx_battles_source_battle (source_battle_id),
	CONSTRAINT fk_battles_course FOREIGN KEY (course_id) REFERENCES courses (id),
	CONSTRAINT fk_battles_unit FOREIGN KEY (unit_id) REFERENCES units (id),
	CONSTRAINT fk_battles_source_battle FOREIGN KEY (source_battle_id) REFERENCES battles (id),
	CONSTRAINT fk_battles_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE battle_participants (
	id                   BIGINT        NOT NULL AUTO_INCREMENT,
	battle_id            BIGINT        NOT NULL,
	user_id              BIGINT        NOT NULL,
	disciple_id          BIGINT        NOT NULL,
	is_creator           BOOLEAN       NOT NULL,
	status               VARCHAR(10)   NOT NULL DEFAULT 'JOINED',
	solve_status         VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
	solve_attempts       TINYINT       NOT NULL DEFAULT 0,
	-- 준비 완료를 누른 순간(연습 배틀은 시작 순간)의 ACTIVE 노트 id 스냅샷. 이미 READY 면 덮어쓰지 않는다.
	note_ids_json        JSON          NULL,
	note_count_snapshot  INT           NOT NULL DEFAULT 0,
	notes_public         BOOLEAN       NOT NULL DEFAULT FALSE,
	-- 부분 정답(+0.5)·감점 때문에 소수가 나오고 음수일 수 있다
	total_score          DECIMAL(6, 2) NOT NULL DEFAULT 0,
	correct_count        INT           NOT NULL DEFAULT 0,
	partial_count        INT           NOT NULL DEFAULT 0,
	wrong_count          INT           NOT NULL DEFAULT 0,
	unknown_count        INT           NOT NULL DEFAULT 0,
	flagged_count        INT           NOT NULL DEFAULT 0,
	result               VARCHAR(12)   NULL,
	joined_at            DATETIME(6)   NULL,
	ready_at             DATETIME(6)   NULL,
	-- 정답 보기(R-11)를 누른 시각. 한 번 채우면 지우지 않는다.
	answers_revealed_at  DATETIME(6)   NULL,
	created_at           DATETIME(6)   NOT NULL,
	updated_at           DATETIME(6)   NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_battle_participants_battle_user UNIQUE (battle_id, user_id),
	INDEX idx_battle_participants_user_created (user_id, created_at),
	INDEX idx_battle_participants_disciple (disciple_id),
	CONSTRAINT fk_battle_participants_battle FOREIGN KEY (battle_id) REFERENCES battles (id),
	CONSTRAINT fk_battle_participants_user FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_battle_participants_disciple FOREIGN KEY (disciple_id) REFERENCES disciples (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE battle_answers (
	id                   BIGINT        NOT NULL AUTO_INCREMENT,
	participant_id       BIGINT        NOT NULL,
	battle_question_id   BIGINT        NOT NULL,
	answer_text          TEXT          NULL,
	is_unknown           BOOLEAN       NOT NULL DEFAULT FALSE,
	cited_note_ids_json  JSON          NOT NULL,
	verdict              VARCHAR(20)   NOT NULL,
	judged_by            VARCHAR(10)   NOT NULL,
	raw_score            DECIMAL(5, 2) NOT NULL,
	copy_ratio           DECIMAL(4, 3) NOT NULL DEFAULT 0,
	copy_factor          DECIMAL(3, 2) NOT NULL DEFAULT 1,
	final_score          DECIMAL(5, 2) NOT NULL,
	injection_flag       BOOLEAN       NOT NULL DEFAULT FALSE,
	cause_suggestion     VARCHAR(20)   NULL,
	grading_detail_json  JSON          NOT NULL,
	created_at           DATETIME(6)   NOT NULL,
	updated_at           DATETIME(6)   NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_battle_answers_participant_question UNIQUE (participant_id, battle_question_id),
	INDEX idx_battle_answers_battle_question (battle_question_id),
	CONSTRAINT fk_battle_answers_participant FOREIGN KEY (participant_id) REFERENCES battle_participants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- battle_answers 와 서로 참조(battle_questions.source_answer_id → battle_answers, battle_answers.battle_question_id → battle_questions)
-- 하므로 battle_answers 를 먼저 만들고 battle_answers 쪽 FK 는 battle_questions 를 만든 뒤에 붙인다.
CREATE TABLE battle_questions (
	id               BIGINT      NOT NULL AUTO_INCREMENT,
	battle_id        BIGINT      NOT NULL,
	question_id      BIGINT      NOT NULL,
	seq              TINYINT     NOT NULL,
	-- PRACTICE 에서 이 문항이 다시 시험하는, 원본 RANKED 배틀의 답
	source_answer_id BIGINT      NULL,
	created_at       DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_battle_questions_battle_seq UNIQUE (battle_id, seq),
	CONSTRAINT uk_battle_questions_battle_question UNIQUE (battle_id, question_id),
	INDEX idx_battle_questions_question (question_id),
	INDEX idx_battle_questions_source_answer (source_answer_id),
	CONSTRAINT fk_battle_questions_battle FOREIGN KEY (battle_id) REFERENCES battles (id),
	CONSTRAINT fk_battle_questions_question FOREIGN KEY (question_id) REFERENCES questions (id),
	CONSTRAINT fk_battle_questions_source_answer FOREIGN KEY (source_answer_id) REFERENCES battle_answers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE battle_answers
	ADD CONSTRAINT fk_battle_answers_battle_question FOREIGN KEY (battle_question_id) REFERENCES battle_questions (id);

ALTER TABLE user_question_history
	ADD CONSTRAINT fk_user_question_history_battle FOREIGN KEY (first_seen_battle_id) REFERENCES battles (id);
