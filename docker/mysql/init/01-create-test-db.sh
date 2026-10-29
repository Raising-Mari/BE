#!/bin/bash
# 최초 1회(볼륨이 비어 있을 때만) 실행된다. 테스트 전용 DB 를 만들고 앱 계정에 권한을 준다.
set -e

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
	CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}_test\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
	GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}_test\`.* TO '${MYSQL_USER}'@'%';
	FLUSH PRIVILEGES;
EOSQL
