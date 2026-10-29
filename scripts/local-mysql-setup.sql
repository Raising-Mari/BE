-- 설치형 MySQL(도커를 쓰지 않을 때)용. 관리자(root) 계정으로 최초 1회만 실행한다.
-- 1) 아래 '여기에_DB_PASSWORD' 를 .env 의 DB_PASSWORD 와 같은 값으로 바꾼다. (이 파일을 수정한 채로 커밋하지 않는다)
-- 2) MySQL Workbench 의 쿼리 창에 붙여 넣어 실행하거나, 터미널에서:  mysql -u root -p < scripts/local-mysql-setup.sql

CREATE DATABASE IF NOT EXISTS disciple      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS disciple_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'disciple'@'localhost' IDENTIFIED BY '여기에_DB_PASSWORD';
GRANT ALL PRIVILEGES ON disciple.*      TO 'disciple'@'localhost';
GRANT ALL PRIVILEGES ON disciple_test.* TO 'disciple'@'localhost';
FLUSH PRIVILEGES;
