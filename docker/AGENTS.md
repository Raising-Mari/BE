# docker/ — 로컬 개발용 컨테이너 설정

`docker-compose.yml`(BE 루트)이 이 폴더를 참조한다. 운영 배포용 설정이 아니라 **개발 PC 전용**이다.

## 규칙
- DB 포트는 `127.0.0.1`에만 열고(`ports` 앞에 `127.0.0.1:`), 팀원은 DB가 아니라 BE API로 접근한다.
- 호스트 포트 기본값은 3306이다. 이 PC에 설치형 MySQL이 3306을 이미 쓰고 있으면 `.env`의 `DB_PORT`를 3307 등으로 바꿔서 띄운다.
- Docker 없이 설치형 MySQL을 쓰는 사람은 이 폴더가 필요 없다. 대신 `scripts/local-mysql-setup.sql`을 쓴다.
- 비밀번호는 `docker-compose.yml`에 쓰지 않고 `.env`에서 받는다.
- `mysql/init/` 스크립트는 **볼륨이 비어 있을 때 최초 1회만** 실행된다. 이미 만든 DB에 반영하려면 마이그레이션(Flyway)을 쓰거나, 데이터를 버려도 될 때만 `docker compose down -v` 후 다시 띄운다. (`-v`는 DB 데이터를 모두 지운다)
- `.sh` 파일은 줄바꿈이 반드시 LF여야 한다. (`.gitattributes`로 고정되어 있다)
