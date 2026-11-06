# domain/course — 과목·단원(주차)·제자

## 책임
과목 검색·생성, 과목별 주차(단원), 제자 만들기(= 수강 등록). **가르칠 범위(단원)는 학생이 직접 정한다.** 무엇을 가르칠지 판단하는 것도 학습의 일부이기 때문이다.
설계: [api-spec.md](../../../../../../../docs/api-spec.md) 2절, 테이블: [erd.md](../../../../../../../docs/erd.md) 6-2·6-3.

## 구조
- `controller/CourseController` — C-01~C-06, D-01·D-02 (경로가 과목 아래라서 한 컨트롤러)
- `service/`
  - `CourseService` — 과목. **`requireVisible(courseId, user)`가 "이 과목을 볼 수 있는가"의 유일한 검사**다. 주차·제자 서비스도 이것을 거친다.
  - `UnitService` — 주차. `DiscipleService` — 제자.
- `entity/` — `Course`, `Unit`, `Disciple`. **제자 엔티티는 이 도메인이 소유한다**(만들기가 곧 수강 등록이라서). 가르치기·배틀은 `DiscipleService`로만 읽고, 제자 저장소를 직접 쓰지 않는다.
- `logic/` — DB를 모르는 순수 클래스: `NameNormalizer`(중복 판정용 이름 정규화), `UnitTitleOrder`(주차 이름 정렬)

## 규칙
- 사용자의 학교·학과는 `auth` 도메인의 `UserLookupService`/`DepartmentService`로 읽는다. `User`·`Department` 엔티티와 저장소를 직접 쓰지 않는다(엔티티는 id로만 가진다).
- **과목은 학교 안에서만 보인다.** 다른 학교의 과목은 없는 과목과 똑같이 `NOT_FOUND`로 답한다. 과목 id로 다른 학교의 존재 여부를 알아내지 못하게 하기 위함이다.
- 과목은 (학교, 정규화한 이름, 학기)가, 주차는 (과목, 정규화한 이름)이, 제자는 (사용자, 과목)이 유일하다. 서비스에서 먼저 확인하고, 동시에 겹치는 경우는 DB UNIQUE가 마지막으로 막는다(`DataIntegrityViolationException` → 중복 코드).
- 중복 오류에는 기존 것의 id를 `details`로 함께 준다(`existingCourseId`, `existingUnitId`). FE가 새로 만들지 않고 그쪽으로 이동하게 하기 위함이다. 동시 경합으로 걸린 경우에는 기존 id를 싣지 않는다.
- 주차는 과목 안에서 모든 학생이 공유한다. 주차를 만들 때 제자(수강 등록)는 필요 없다.
- 주차 목록의 `materialCount`·`myNoteCount`·`battleReady`는 해당 기능(자료 WP5·노트 6-B·문제 은행 WP7)이 들어오기 전까지 0·0·false다. 각 기능이 들어올 때 **그 도메인의 서비스를 불러** `UnitService.list`를 채운다. 그 도메인의 저장소를 직접 조회하지 않는다.
- 단원은 학생이 만들고 고르는 것이다. 서버가 자료에서 뽑은 개념 목록으로 단원을 대신 정해 주지 않는다.
- 과목 보관(`ARCHIVED`)과 시연용 과목은 SQL로 처리한다. 보관된 과목은 검색에서만 빠진다.
- 시연용 과목은 공개 자료로만 만든다. (아직 미정)
