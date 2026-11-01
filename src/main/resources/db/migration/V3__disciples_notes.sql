-- 제자·대화·노트·탈락한 노트 변경안 (docs/erd.md 6-3)

-- 제자를 만드는 것이 곧 그 과목 "수강 등록"이다. 과목마다 1명.
CREATE TABLE disciples (
	id            BIGINT      NOT NULL AUTO_INCREMENT,
	user_id       BIGINT      NOT NULL,
	course_id     BIGINT      NOT NULL,
	name          VARCHAR(30) NOT NULL,
	character_key VARCHAR(30) NULL,
	created_at    DATETIME(6) NOT NULL,
	updated_at    DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_disciples_user_course UNIQUE (user_id, course_id),
	INDEX idx_disciples_course (course_id),
	CONSTRAINT fk_disciples_user FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_disciples_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE chat_messages (
	id                BIGINT      NOT NULL AUTO_INCREMENT,
	disciple_id       BIGINT      NOT NULL,
	unit_id           BIGINT      NOT NULL,
	role              VARCHAR(10) NOT NULL,
	content           TEXT        NOT NULL,
	turn_no           INT         NOT NULL,
	client_message_id CHAR(36)    NULL,
	asked_why         BOOLEAN     NOT NULL DEFAULT FALSE,
	ai_call_log_id    BIGINT      NULL,
	created_at        DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	-- 같은 메시지를 두 번 보내도 한 번만 반영한다 (client_message_id 는 학생 메시지에만 있어 NULL 은 중복으로 보지 않는다)
	CONSTRAINT uk_chat_messages_client_id UNIQUE (disciple_id, client_message_id),
	-- 동시에 두 번 전송했을 때 턴 번호가 겹치는 것을 막는다
	CONSTRAINT uk_chat_messages_turn UNIQUE (disciple_id, unit_id, turn_no, role),
	INDEX idx_chat_messages_thread (disciple_id, unit_id, id),
	CONSTRAINT fk_chat_messages_disciple FOREIGN KEY (disciple_id) REFERENCES disciples (id),
	CONSTRAINT fk_chat_messages_unit FOREIGN KEY (unit_id) REFERENCES units (id),
	CONSTRAINT fk_chat_messages_ai_call_log FOREIGN KEY (ai_call_log_id) REFERENCES ai_call_logs (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 노트는 한번 저장하면 내용을 고치지 않는다. 고쳐 말하면 새 행이 생기고 옛 행은 SUPERSEDED/REMOVED/MERGED 가 된다.
CREATE TABLE note_entries (
	id                       BIGINT      NOT NULL AUTO_INCREMENT,
	disciple_id              BIGINT      NOT NULL,
	unit_id                  BIGINT      NOT NULL,
	content                  TEXT        NOT NULL,
	evidence                 TEXT        NOT NULL,
	source_message_id        BIGINT      NOT NULL,
	status                   VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
	replaces_entry_id        BIGINT      NULL,
	merged_into_entry_id     BIGINT      NULL,
	version                  INT         NOT NULL DEFAULT 1,
	verification_detail_json JSON        NOT NULL,
	created_at               DATETIME(6) NOT NULL,
	updated_at               DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_note_entries_disciple_unit_status (disciple_id, unit_id, status),
	CONSTRAINT fk_note_entries_disciple FOREIGN KEY (disciple_id) REFERENCES disciples (id),
	CONSTRAINT fk_note_entries_unit FOREIGN KEY (unit_id) REFERENCES units (id),
	CONSTRAINT fk_note_entries_source_message FOREIGN KEY (source_message_id) REFERENCES chat_messages (id),
	CONSTRAINT fk_note_entries_replaces FOREIGN KEY (replaces_entry_id) REFERENCES note_entries (id),
	CONSTRAINT fk_note_entries_merged_into FOREIGN KEY (merged_into_entry_id) REFERENCES note_entries (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 서버 검증에서 탈락한 AI의 노트 변경안. 중복(유사도 >= 0.9)은 탈락이 아니라 병합이므로 남기지 않는다.
CREATE TABLE rejected_note_changes (
	id                 BIGINT      NOT NULL AUTO_INCREMENT,
	disciple_id        BIGINT      NOT NULL,
	message_id         BIGINT      NOT NULL,
	op                 VARCHAR(10) NOT NULL,
	target_entry_id    BIGINT      NULL,
	proposed_content   TEXT        NULL,
	proposed_evidence  TEXT        NULL,
	failed_checks_json JSON        NOT NULL,
	created_at         DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_rejected_note_changes_message (message_id),
	CONSTRAINT fk_rejected_note_changes_disciple FOREIGN KEY (disciple_id) REFERENCES disciples (id),
	CONSTRAINT fk_rejected_note_changes_message FOREIGN KEY (message_id) REFERENCES chat_messages (id),
	CONSTRAINT fk_rejected_note_changes_target FOREIGN KEY (target_entry_id) REFERENCES note_entries (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
