-- 기초·인증: 학교, 학과, 사용자, AI 호출 로그 (docs/erd.md 6-1)
-- 시각 컬럼에 DEFAULT CURRENT_TIMESTAMP 를 쓰지 않는다. 설치형 MySQL 은 서버 시간대가 KST 라 UTC 가 깨진다. 앱이 채우고 시드는 UTC_TIMESTAMP(6) 를 쓴다.

CREATE TABLE schools (
	id           BIGINT       NOT NULL AUTO_INCREMENT,
	code         VARCHAR(20)  NOT NULL,
	name         VARCHAR(100) NOT NULL,
	email_domain VARCHAR(100) NOT NULL,
	created_at   DATETIME(6)  NOT NULL,
	updated_at   DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_schools_code UNIQUE (code),
	CONSTRAINT uk_schools_email_domain UNIQUE (email_domain)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE departments (
	id           BIGINT       NOT NULL AUTO_INCREMENT,
	school_id    BIGINT       NOT NULL,
	college_name VARCHAR(100) NOT NULL,
	name         VARCHAR(100) NOT NULL,
	created_at   DATETIME(6)  NOT NULL,
	updated_at   DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_departments_school_name UNIQUE (school_id, name),
	CONSTRAINT fk_departments_school FOREIGN KEY (school_id) REFERENCES schools (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE users (
	id              BIGINT       NOT NULL AUTO_INCREMENT,
	school_id       BIGINT       NOT NULL,
	department_id   BIGINT       NULL,
	google_sub      VARCHAR(255) NOT NULL,
	email           VARCHAR(255) NOT NULL,
	nickname        VARCHAR(20)  NOT NULL,
	role            VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
	status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
	terms_agreed_at DATETIME(6)  NOT NULL,
	last_login_at   DATETIME(6)  NULL,
	created_at      DATETIME(6)  NOT NULL,
	updated_at      DATETIME(6)  NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_users_google_sub UNIQUE (google_sub),
	CONSTRAINT uk_users_email UNIQUE (email),
	CONSTRAINT uk_users_nickname UNIQUE (nickname),
	INDEX idx_users_school_department (school_id, department_id),
	CONSTRAINT fk_users_school FOREIGN KEY (school_id) REFERENCES schools (id),
	CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 프롬프트·응답 원문과 학생 입력은 저장하지 않는다. user_id 는 사용자 행과 무관하게 로그를 보존하려고 FK 를 걸지 않는다.
CREATE TABLE ai_call_logs (
	id             BIGINT      NOT NULL AUTO_INCREMENT,
	purpose        VARCHAR(30) NOT NULL,
	provider       VARCHAR(20) NOT NULL,
	model          VARCHAR(50) NOT NULL,
	prompt_name    VARCHAR(50) NOT NULL,
	prompt_version VARCHAR(10) NOT NULL,
	input_tokens   INT         NULL,
	output_tokens  INT         NULL,
	latency_ms     INT         NOT NULL,
	success        BOOLEAN     NOT NULL,
	error_code     VARCHAR(40) NULL,
	user_id        BIGINT      NULL,
	related_type   VARCHAR(30) NULL,
	related_id     BIGINT      NULL,
	created_at     DATETIME(6) NOT NULL,
	PRIMARY KEY (id),
	INDEX idx_ai_call_logs_created_at (created_at),
	INDEX idx_ai_call_logs_purpose_created_at (purpose, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 시드: 명지대학교. email_domain 은 Google ID 토큰의 hd 값과 비교한다 (2026-11-02 확인: mju.ac.kr)
INSERT INTO schools (code, name, email_domain, created_at, updated_at)
VALUES ('MJU', '명지대학교', 'mju.ac.kr', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- 시드: 명지대 학과 목록 (2026-11-02 기준). 전공이 있는 학부는 전공을, 전공이 없는 학부·학과는 그 이름을 가입 화면의 선택지로 둔다.
SET @mju_id = (SELECT id FROM schools WHERE code = 'MJU');

INSERT INTO departments (school_id, college_name, name, created_at, updated_at) VALUES
	(@mju_id, '인문대학', '중어중문학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '일어일문학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '아랍지역학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '글로벌한국어학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '국어국문학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '영어영문학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '미술사·역사학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '문헌정보학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '글로벌문화콘텐츠학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인문대학', '문예창작학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '사회과학대학', '행정학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '사회과학대학', '정치외교학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '사회과학대학', '경제학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '사회과학대학', '국제통상학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '사회과학대학', '응용통계학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '사회과학대학', '법학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '경영대학', '경영학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '경영대학', '글로벌비즈니스학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '경영대학', '경영정보학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '경영대학', '국제통상학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '미디어·휴먼라이프대학', '디지털미디어학부', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미디어·휴먼라이프대학', '청소년지도학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미디어·휴먼라이프대학', '아동학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '인공지능소프트웨어융합대학', '응용소프트웨어전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인공지능소프트웨어융합대학', '데이터사이언스전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인공지능소프트웨어융합대학', '인공지능전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '인공지능소프트웨어융합대학', '디지털콘텐츠디자인학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '미래융합대학', '창의융합인재학부', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '사회복지학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '부동산학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '법무행정학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '심리치료학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '미래융합경영학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '멀티디자인학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '회계세무학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '미래융합대학', '계약학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '아너칼리지대학', '아너칼리지(전공자유대학)', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '아너칼리지대학', '자율전공학부(인문)', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '아너칼리지대학', '자율전공학부(자연)', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '화학·생명과학대학', '화학나노학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '화학·생명과학대학', '융합에너지학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '화학·생명과학대학', '식품영양학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '화학·생명과학대학', '시스템생명과학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '화학·생명과학대학', '물리학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '화학·생명과학대학', '수학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '스마트시스템공과대학', '화학공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', '신소재공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', '환경시스템공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', '건설환경공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', '스마트모빌리티공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', 'GLOBAL SMART INFRASTRUCTURE ENGINEERING MAJOR', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', '기계공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스마트시스템공과대학', '로봇공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '반도체·ICT대학', '컴퓨터공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '반도체·ICT대학', '정보통신공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '반도체·ICT대학', '전기공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '반도체·ICT대학', '전자공학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '반도체·ICT대학', '반도체공학부', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '반도체·ICT대학', '산업경영공학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '스포츠·예술대학', '비주얼커뮤니케이션디자인전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '인더스트리얼디자인전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '영상애니메이션디자인전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '패션디자인전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '체육학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '스포츠산업학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '스포츠지도학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '건반음악전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '보컬뮤직전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '작곡전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '연극·영화전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '뮤지컬공연전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '스포츠·예술대학', '바둑학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),

	(@mju_id, '건축대학', '건축학전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '건축대학', '전통건축전공', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
	(@mju_id, '건축대학', '공간디자인학과', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
