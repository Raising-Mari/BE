# global/error/ — 에러 처리

- 에러 응답 형식은 `ErrorResponse` 하나다: `{ code, message, violations[] }`. FE는 `code` 문자열로 분기한다.
- **한 번 정한 `ErrorCode`의 이름은 바꾸지 않는다.** (FE가 문자열로 비교한다) 바꾸려면 FE와 먼저 합의한다.
- 도메인 에러 코드는 `ErrorCode`에 접두사를 붙여 추가한다. 예: `AUTH_EMAIL_NOT_ALLOWED`, `BATTLE_ROOM_FULL`.
- 서비스에서는 `BusinessException(ErrorCode)`만 던진다. `RuntimeException`·`IllegalStateException`을 그대로 던지지 않는다.
- `GlobalExceptionHandler`의 마지막 핸들러는 내부 예외 메시지를 응답에 싣지 않는다. (스택트레이스·SQL 노출 방지)
- 메시지는 학생에게 보여도 되는 한국어 문장으로 쓴다.
