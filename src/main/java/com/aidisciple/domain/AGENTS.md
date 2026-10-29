# domain/ — 기능별 코드

도메인 하나 = 폴더 하나. 새 도메인은 아래 구조로 만든다.

```
domain/<이름>/
├─ controller/   REST 엔드포인트 (요청·응답 변환만)
├─ service/      업무 로직과 트랜잭션
├─ repository/   Spring Data JPA 인터페이스
├─ entity/       JPA 엔티티, enum
├─ dto/          요청·응답 record
└─ (검증·계산 로직은 service와 분리한 순수 클래스로 둔다)
```

## 규칙
- **의존 방향**: `controller → service → repository`. 거꾸로 부르지 않는다.
- 다른 도메인이 필요하면 **그 도메인의 `service`**를 호출한다. 다른 도메인의 `repository`·`entity` 내부를 직접 쓰지 않는다.
- 도메인 사이 순환 참조가 생기면 구조가 잘못된 것이다. 한쪽을 이벤트나 별도 서비스로 분리한다.
- 점수 계산, 노트 검증, Elo 같은 **핵심 규칙은 DB·AI를 모르는 순수 클래스**로 만들고 단위 테스트를 반드시 붙인다.
- 기준값(점수, 임계값, 제한 횟수)은 코드 곳곳에 숫자로 흩뿌리지 않고, 상수 한 곳 또는 `application.yml`의 `app.*`에 모은다.
- 컨트롤러 메서드에는 `@Operation(summary = ...)`을 달아 Swagger에서 읽히게 한다. FE는 이 명세로 타입을 만든다.
- API를 바꾸면(경로·필드·에러 코드) 같은 PR 설명에 **FE에 영향이 있다고 명시**한다.

## 현재 도메인
`auth` · `course` · `material` · `teaching` · `question` · `battle` · `grading` · `review` · `ranking`
(각 폴더의 `AGENTS.md`에 그 도메인만의 규칙이 있다. 구현 전에는 폴더에 이 파일만 있다.)
