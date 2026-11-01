-- 과목·주차(units)·강의자료·문장·개념 (docs/erd.md 6-2)
-- 단원(unit)은 곧 주차다. 원본 파일은 저장하지 않고 추출한 문장만 남긴다.

CREATE TABLE courses (
	id              BIGINT       NOT NULL AUTO_INCREMENT,
	school_id       BIGINT       NOT NULL,
	department_id   BIGINT       NULL,
	name            VARCHAR(100) NOT NULL,
	normalized_name VARCHAR(100) NOT NULL,
	term            VARCHAR(10)  NOT NULL,
	status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
	created_by      BIGINT       NOT NULL,
	created_at      DATETIME(6)  NOT NULL,
	updated_at      DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	-- 같은 과목을 학생마다 따로 만들면 친구와 겨룰 수 없으므로 중복 생성을 막는다
	CONSTRAINT uk_courses_school_name_term UNIQUE (school_id, normalized_name, term),
	CONSTRAINT fk_courses_school FOREIGN KEY (school_id) REFERENCES schools (id),
	CONSTRAINT fk_courses_department FOREIGN KEY (department_id) REFERENCES departments (id),
	CONSTRAINT fk_courses_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE units (
	id               BIGINT       NOT NULL AUTO_INCREMENT,
	course_id        BIGINT       NOT NULL,
	title            VARCHAR(100) NOT NULL,
	normalized_title VARCHAR(100) NOT NULL,
	created_by       BIGINT       NOT NULL,
	created_at       DATETIME(6)  NOT NULL,
	updated_at       DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	-- 주차는 과목 안에서 공유된다 (친구와 같은 주차로 배틀하기 위함)
	CONSTRAINT uk_units_course_title UNIQUE (course_id, normalized_title),
	CONSTRAINT fk_units_course FOREIGN KEY (course_id) REFERENCES courses (id),
	CONSTRAINT fk_units_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE materials (
	id                BIGINT       NOT NULL AUTO_INCREMENT,
	course_id         BIGINT       NOT NULL,
	uploader_id       BIGINT       NOT NULL,
	title             VARCHAR(200) NOT NULL,
	source_type       VARCHAR(10)  NOT NULL,
	original_filename VARCHAR(255) NULL,
	file_size_bytes   BIGINT       NULL,
	status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
	fail_reason       VARCHAR(30)  NULL,
	char_count        INT          NOT NULL DEFAULT 0,
	sentence_count    INT          NOT NULL DEFAULT 0,
	consent_agreed_at DATETIME(6)  NOT NULL,
	processed_at      DATETIME(6)  NULL,
	created_at        DATETIME(6)  NOT NULL,
	updated_at        DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_materials_course_status (course_id, status),
	INDEX idx_materials_uploader (uploader_id),
	CONSTRAINT fk_materials_course FOREIGN KEY (course_id) REFERENCES courses (id),
	CONSTRAINT fk_materials_uploader FOREIGN KEY (uploader_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 자료와 주차의 다대다 연결. 같은 과목의 주차만 연결할 수 있다는 검사는 서버가 한다.
CREATE TABLE material_units (
	material_id BIGINT      NOT NULL,
	unit_id     BIGINT      NOT NULL,
	created_at  DATETIME(6) NOT NULL,
	PRIMARY KEY (material_id, unit_id),
	INDEX idx_material_units_unit (unit_id),
	CONSTRAINT fk_material_units_material FOREIGN KEY (material_id) REFERENCES materials (id) ON DELETE CASCADE,
	CONSTRAINT fk_material_units_unit FOREIGN KEY (unit_id) REFERENCES units (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE material_sentences (
	id              BIGINT      NOT NULL AUTO_INCREMENT,
	material_id     BIGINT      NOT NULL,
	seq             INT         NOT NULL,
	page_no         INT         NULL,
	text            TEXT        NOT NULL,
	normalized_text TEXT        NOT NULL,
	text_hash       CHAR(64)    NOT NULL,
	created_at      DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_material_sentences_material_seq UNIQUE (material_id, seq),
	INDEX idx_material_sentences_hash (text_hash),
	CONSTRAINT fk_material_sentences_material FOREIGN KEY (material_id) REFERENCES materials (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 학생에게 노출하지 않는 개념 목록. 어떤 API 응답에도 담지 않는다.
CREATE TABLE concepts (
	id                       BIGINT       NOT NULL AUTO_INCREMENT,
	unit_id                  BIGINT       NOT NULL,
	label                    VARCHAR(200) NOT NULL,
	description              TEXT         NULL,
	source_sentence_ids_json JSON         NOT NULL,
	status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
	created_at               DATETIME(6)  NOT NULL,
	updated_at               DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_concepts_unit (unit_id),
	CONSTRAINT fk_concepts_unit FOREIGN KEY (unit_id) REFERENCES units (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
