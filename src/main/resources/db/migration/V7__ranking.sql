-- 랭킹: 과목별 Elo 와 배틀별 변동 이력 (docs/erd.md 6-7). RANKED 배틀만 반영한다.

CREATE TABLE elo_ratings (
	id           BIGINT      NOT NULL AUTO_INCREMENT,
	user_id      BIGINT      NOT NULL,
	course_id    BIGINT      NOT NULL,
	rating       INT         NOT NULL DEFAULT 1000,
	games_played INT         NOT NULL DEFAULT 0,
	wins         INT         NOT NULL DEFAULT 0,
	losses       INT         NOT NULL DEFAULT 0,
	draws        INT         NOT NULL DEFAULT 0,
	created_at   DATETIME(6) NOT NULL,
	updated_at   DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_elo_ratings_user_course UNIQUE (user_id, course_id),
	INDEX idx_elo_ratings_course_rating (course_id, rating, user_id),
	CONSTRAINT fk_elo_ratings_user FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_elo_ratings_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE elo_histories (
	id            BIGINT      NOT NULL AUTO_INCREMENT,
	user_id       BIGINT      NOT NULL,
	course_id     BIGINT      NOT NULL,
	battle_id     BIGINT      NOT NULL,
	rating_before INT         NOT NULL,
	rating_after  INT         NOT NULL,
	delta         INT         NOT NULL,
	created_at    DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	-- 같은 배틀의 Elo 를 두 번 반영하는 것을 막는다 (멱등성)
	CONSTRAINT uk_elo_histories_battle_user UNIQUE (battle_id, user_id),
	INDEX idx_elo_histories_course_created (course_id, created_at),
	INDEX idx_elo_histories_user (user_id),
	CONSTRAINT fk_elo_histories_user FOREIGN KEY (user_id) REFERENCES users (id),
	CONSTRAINT fk_elo_histories_course FOREIGN KEY (course_id) REFERENCES courses (id),
	CONSTRAINT fk_elo_histories_battle FOREIGN KEY (battle_id) REFERENCES battles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
