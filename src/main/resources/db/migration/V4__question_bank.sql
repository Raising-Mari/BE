-- 문제 은행: 생성 작업, 통과한 문항, 근거, 단계별 검증 로그, 학생별 본 문항 (docs/erd.md 6-4)

CREATE TABLE question_generation_jobs (
	id             BIGINT       NOT NULL AUTO_INCREMENT,
	unit_id        BIGINT       NOT NULL,
	trigger_type   VARCHAR(20)  NOT NULL,
	requested_by   BIGINT       NULL,
	target_count   INT          NOT NULL,
	status         VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
	generated_count INT         NOT NULL DEFAULT 0,
	passed_count   INT          NOT NULL DEFAULT 0,
	rejected_count INT          NOT NULL DEFAULT 0,
	fail_reason    VARCHAR(100) NULL,
	started_at     DATETIME(6)  NULL,
	finished_at    DATETIME(6)  NULL,
	created_at     DATETIME(6)  NOT NULL,
	updated_at     DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_question_generation_jobs_unit_status (unit_id, status),
	CONSTRAINT fk_question_generation_jobs_unit FOREIGN KEY (unit_id) REFERENCES units (id),
	CONSTRAINT fk_question_generation_jobs_requested_by FOREIGN KEY (requested_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 통과한 문항만 저장한다. 탈락 문항은 question_validation_logs.candidate_json 에만 남는다.
CREATE TABLE questions (
	id                    BIGINT        NOT NULL AUTO_INCREMENT,
	course_id             BIGINT        NOT NULL,
	unit_id               BIGINT        NOT NULL,
	concept_id            BIGINT        NULL,
	stem                  TEXT          NOT NULL,
	grading_mode          VARCHAR(15)   NOT NULL,
	answers_json          JSON          NULL,
	key_elements_json     JSON          NULL,
	required_elements     TINYINT       NULL,
	reference_answer      TEXT          NOT NULL,
	evidence_text         TEXT          NOT NULL,
	status                VARCHAR(20)   NOT NULL,
	source                VARCHAR(10)   NOT NULL,
	leak_test_passed      BOOLEAN       NOT NULL,
	external_judge_score  DECIMAL(4, 3) NULL,
	external_judge_model  VARCHAR(50)   NULL,
	generation_job_id     BIGINT        NULL,
	reviewed_by           BIGINT        NULL,
	reviewed_at           DATETIME(6)   NULL,
	created_at            DATETIME(6)   NOT NULL,
	updated_at            DATETIME(6)   NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_questions_unit_status (unit_id, status),
	INDEX idx_questions_concept (concept_id),
	CONSTRAINT fk_questions_course FOREIGN KEY (course_id) REFERENCES courses (id),
	CONSTRAINT fk_questions_unit FOREIGN KEY (unit_id) REFERENCES units (id),
	CONSTRAINT fk_questions_concept FOREIGN KEY (concept_id) REFERENCES concepts (id),
	CONSTRAINT fk_questions_generation_job FOREIGN KEY (generation_job_id) REFERENCES question_generation_jobs (id),
	CONSTRAINT fk_questions_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 자료를 삭제하거나 주차 연결을 풀 때 이 근거를 쓰던 문항을 RETIRED 로 바꾸는 데 쓴다.
CREATE TABLE question_evidences (
	question_id BIGINT      NOT NULL,
	sentence_id BIGINT      NOT NULL,
	created_at  DATETIME(6) NOT NULL,
	PRIMARY KEY (question_id, sentence_id),
	INDEX idx_question_evidences_sentence (sentence_id),
	CONSTRAINT fk_question_evidences_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
	CONSTRAINT fk_question_evidences_sentence FOREIGN KEY (sentence_id) REFERENCES material_sentences (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_validation_logs (
	id             BIGINT        NOT NULL AUTO_INCREMENT,
	job_id         BIGINT        NOT NULL,
	question_id    BIGINT        NULL,
	stage          VARCHAR(20)   NOT NULL,
	passed         BOOLEAN       NOT NULL,
	score          DECIMAL(4, 3) NULL,
	candidate_json JSON          NOT NULL,
	detail_json    JSON          NULL,
	created_at     DATETIME(6)   NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_question_validation_logs_job (job_id),
	INDEX idx_question_validation_logs_question (question_id),
	CONSTRAINT fk_question_validation_logs_job FOREIGN KEY (job_id) REFERENCES question_generation_jobs (id),
	CONSTRAINT fk_question_validation_logs_question FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "한 번 본 문항은 재대결에서 제외"를 빠르게 판단한다. 랭킹 배틀의 문항만 기록하고 연습 배틀은 쓰지 않는다.
CREATE TABLE user_question_history (
	id                    BIGINT      NOT NULL AUTO_INCREMENT,
	user_id               BIGINT      NOT NULL,
	question_id           BIGINT      NOT NULL,
	source                VARCHAR(10) NOT NULL DEFAULT 'BATTLE',
	first_seen_battle_id  BIGINT      NULL,
	created_at            DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_user_question_history_user_question UNIQUE (user_id, question_id),
	CONSTRAINT fk_user_question_history_user FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_user_question_history_question FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
