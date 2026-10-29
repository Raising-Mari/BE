# global/jpa/ — 엔티티 공통

- 모든 엔티티는 `BaseTimeEntity`를 상속해 `createdAt`·`updatedAt`(UTC `Instant`)을 갖는다.
- 연관관계는 기본 `LAZY`. 즉시 로딩(`EAGER`)을 쓰지 않는다.
- 컬렉션을 한 번에 가져와야 하면 `fetch join` 또는 `@EntityGraph`를 쓴다. (N+1 방지)
- 엔티티 `equals/hashCode`는 식별자 기준으로만 쓰거나, 구현하지 않는다.
- 스키마를 바꾸는 엔티티 수정은 반드시 Flyway 마이그레이션과 한 PR에 담는다.
