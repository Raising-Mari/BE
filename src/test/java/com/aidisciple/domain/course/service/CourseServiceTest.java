package com.aidisciple.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.DepartmentResponse;
import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.DepartmentService;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.CourseResponse;
import com.aidisciple.domain.course.dto.CreateCourseRequest;
import com.aidisciple.domain.course.dto.MyCourseResponse;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.repository.CourseRepository;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class CourseServiceTest {

	private static final long USER_ID = 42L;
	private static final long SCHOOL_ID = 1L;
	private static final long OTHER_SCHOOL_ID = 2L;

	private final CourseRepository courseRepository = mock(CourseRepository.class);
	private final DiscipleRepository discipleRepository = mock(DiscipleRepository.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final DepartmentService departmentService = mock(DepartmentService.class);
	private final CourseService service = new CourseService(courseRepository, discipleRepository, userLookupService, departmentService);

	private final DepartmentResponse computerScience = new DepartmentResponse(7L, "컴퓨터공학전공", "반도체·ICT대학");

	@BeforeEach
	void 기본_상태() {
		when(userLookupService.requireActive(USER_ID)).thenReturn(new UserRef(USER_ID, SCHOOL_ID, null));
		when(courseRepository.saveAndFlush(any(Course.class))).thenAnswer(invocation -> {
			Course saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);   // 실제로는 DB 가 정하는 값
			return saved;
		});
	}

	private static Course course(long id, long schoolId, String name, String term, Long departmentId) {
		Course course = Course.create(schoolId, departmentId, name, name.replace(" ", "").toLowerCase(), term, 1L);
		ReflectionTestUtils.setField(course, "id", id);
		return course;
	}

	private static Disciple disciple(long id, long userId, long courseId) {
		Disciple disciple = BeanUtils.instantiateClass(Disciple.class);
		ReflectionTestUtils.setField(disciple, "id", id);
		ReflectionTestUtils.setField(disciple, "userId", userId);
		ReflectionTestUtils.setField(disciple, "courseId", courseId);
		ReflectionTestUtils.setField(disciple, "name", "제자" + id);
		ReflectionTestUtils.setField(disciple, "characterKey", "cat");
		return disciple;
	}

	/** 동작을 한 번만 실행하고, 던져진 업무 예외를 돌려준다. */
	private static BusinessException thrownBy(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	private static CreateCourseRequest request(String name, Long departmentId, String term) {
		return new CreateCourseRequest(name, departmentId, term);
	}

	// --- C-02 ---

	@Test
	void 과목을_만들면_내_학교로_정규화한_이름과_함께_저장한다() {
		when(departmentService.findInSchool(7L, SCHOOL_ID)).thenReturn(Optional.of(computerScience));

		CourseResponse response = service.create(USER_ID, request("  자료 구조  ", 7L, " 2026-2 "));

		ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
		verify(courseRepository).saveAndFlush(saved.capture());
		Course course = saved.getValue();
		assertThat(course.getSchoolId()).isEqualTo(SCHOOL_ID);
		assertThat(course.getDepartmentId()).isEqualTo(7L);
		assertThat(course.getName()).isEqualTo("자료 구조");
		assertThat(course.getNormalizedName()).isEqualTo("자료구조");
		assertThat(course.getTerm()).isEqualTo("2026-2");
		assertThat(course.getCreatedBy()).isEqualTo(USER_ID);

		assertThat(response).isEqualTo(new CourseResponse(100L, "자료 구조", "2026-2",
			new CourseResponse.Department(7L, "컴퓨터공학전공"), false));
	}

	@Test
	void 학과는_생략할_수_있다() {
		CourseResponse response = service.create(USER_ID, request("글쓰기", null, "2026-2"));

		assertThat(response.department()).isNull();
		verify(departmentService, never()).findInSchool(anyLong(), anyLong());
	}

	@Test
	void 같은_학교_같은_학기의_같은_이름이면_기존_과목_id와_함께_거부한다() {
		when(courseRepository.findBySchoolIdAndNormalizedNameAndTerm(SCHOOL_ID, "자료구조", "2026-2"))
			.thenReturn(Optional.of(course(12L, SCHOOL_ID, "자료구조", "2026-2", null)));

		BusinessException e = thrownBy(() -> service.create(USER_ID, request("자료 구조", null, "2026-2")));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COURSE_DUPLICATE);
		assertThat(e.getDetails()).containsEntry("existingCourseId", 12L);
		verify(courseRepository, never()).saveAndFlush(any());
	}

	@Test
	void 학기가_다르면_같은_이름이어도_새_과목이다() {
		service.create(USER_ID, request("자료구조", null, "2026-1"));

		verify(courseRepository).findBySchoolIdAndNormalizedNameAndTerm(SCHOOL_ID, "자료구조", "2026-1");
		verify(courseRepository).saveAndFlush(any(Course.class));
	}

	@Test
	void 학기_형식이_틀리면_거부한다() {
		for (String term : new String[] {"2026", "2026-", "2026-0", "2026-10", "26-2", "2026/2", "abcd-2", "2026-2학기"}) {
			assertThat(thrownBy(() -> service.create(USER_ID, request("자료구조", null, term))).getErrorCode())
				.as(term).isEqualTo(ErrorCode.INVALID_INPUT);
		}
		verify(courseRepository, never()).saveAndFlush(any());
	}

	@Test
	void 이름이_보이지_않는_문자뿐이면_거부한다() {
		assertThat(thrownBy(() -> service.create(USER_ID, request("" + (char) 0x200B + ' ' + (char) 0x3000, null, "2026-2"))).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);
	}

	@Test
	void 없는_학과나_다른_학교_학과면_거부한다() {
		when(departmentService.findInSchool(9L, SCHOOL_ID)).thenReturn(Optional.empty());

		assertThat(thrownBy(() -> service.create(USER_ID, request("자료구조", 9L, "2026-2"))).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);
		verify(courseRepository, never()).saveAndFlush(any());
	}

	@Test
	void 동시에_만들어_DB_UNIQUE에_걸리면_중복으로_답한다() {
		when(courseRepository.saveAndFlush(any(Course.class))).thenThrow(new DataIntegrityViolationException("dup"));

		BusinessException e = thrownBy(() -> service.create(USER_ID, request("자료구조", null, "2026-2")));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COURSE_DUPLICATE);
		assertThat(e.getDetails()).isEmpty();
	}

	// --- 접근 ---

	@Test
	void 다른_학교의_과목과_없는_과목은_똑같이_NOT_FOUND다() {
		when(courseRepository.findById(5L)).thenReturn(Optional.of(course(5L, OTHER_SCHOOL_ID, "타교과목", "2026-2", null)));
		when(courseRepository.findById(6L)).thenReturn(Optional.empty());
		UserRef user = new UserRef(USER_ID, SCHOOL_ID, null);

		assertThat(thrownBy(() -> service.requireVisible(5L, user)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(thrownBy(() -> service.requireVisible(6L, user)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void 같은_학교의_과목은_볼_수_있다() {
		Course mine = course(5L, SCHOOL_ID, "자료구조", "2026-2", null);
		when(courseRepository.findById(5L)).thenReturn(Optional.of(mine));

		assertThat(service.requireVisible(5L, new UserRef(USER_ID, SCHOOL_ID, null))).isSameAs(mine);
	}

	// --- C-04 ---

	@Test
	void 과목_상세는_내_제자_유무와_학과를_함께_준다() {
		when(courseRepository.findById(5L)).thenReturn(Optional.of(course(5L, SCHOOL_ID, "자료구조", "2026-2", 7L)));
		when(departmentService.findByIds(anyCollection())).thenReturn(Map.of(7L, computerScience));
		when(discipleRepository.existsByUserIdAndCourseId(USER_ID, 5L)).thenReturn(true);

		CourseResponse response = service.get(USER_ID, 5L);

		assertThat(response.hasMyDisciple()).isTrue();
		assertThat(response.department()).isEqualTo(new CourseResponse.Department(7L, "컴퓨터공학전공"));
	}

	@Test
	void 다른_학교_과목의_상세는_404다() {
		when(courseRepository.findById(5L)).thenReturn(Optional.of(course(5L, OTHER_SCHOOL_ID, "타교과목", "2026-2", null)));

		assertThat(thrownBy(() -> service.get(USER_ID, 5L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

	// --- C-03 ---

	@Test
	void 내_과목은_최근_만든_제자의_과목부터_제자와_함께_준다() {
		when(discipleRepository.findByUserIdOrderByIdDesc(USER_ID)).thenReturn(List.of(
			disciple(30L, USER_ID, 2L), disciple(20L, USER_ID, 1L), disciple(10L, USER_ID, 999L)));   // 999 번 과목은 사라진 상태
		when(courseRepository.findAllById(anyCollection())).thenReturn(List.of(
			course(1L, SCHOOL_ID, "자료구조", "2026-2", null), course(2L, SCHOOL_ID, "알고리즘", "2026-2", null)));

		List<MyCourseResponse> result = service.myCourses(USER_ID);

		assertThat(result).extracting(MyCourseResponse::id).containsExactly(2L, 1L);
		assertThat(result.get(0).disciple()).isEqualTo(new MyCourseResponse.Disciple(30L, "제자30", "cat"));
	}

	@Test
	void 제자가_없으면_빈_목록이다() {
		when(discipleRepository.findByUserIdOrderByIdDesc(USER_ID)).thenReturn(List.of());

		assertThat(service.myCourses(USER_ID)).isEmpty();
	}

	// --- C-01 ---

	@SuppressWarnings("unchecked")
	@Test
	void 검색은_페이지_크기를_50으로_줄이고_학기_최신순_이름순으로_정렬한다() {
		when(courseRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of()));

		service.search(USER_ID, "자료", null, 0, 999);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(courseRepository).findAll(any(Specification.class), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("term"), Sort.Order.asc("name")));
	}

	@SuppressWarnings("unchecked")
	@Test
	void 검색_결과에는_내_제자_유무와_학과_이름이_붙는다() {
		Course withDisciple = course(1L, SCHOOL_ID, "자료구조", "2026-2", 7L);
		Course without = course(2L, SCHOOL_ID, "알고리즘", "2026-2", null);
		when(courseRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(withDisciple, without), org.springframework.data.domain.PageRequest.of(0, 20), 2));
		when(discipleRepository.findByUserIdAndCourseIdIn(any(), anyCollection())).thenReturn(List.of(disciple(10L, USER_ID, 1L)));
		when(departmentService.findByIds(anyCollection())).thenReturn(Map.of(7L, computerScience));

		PageResponse<CourseResponse> page = service.search(USER_ID, null, null, null, null);

		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.items()).extracting(CourseResponse::hasMyDisciple).containsExactly(true, false);
		assertThat(page.items().get(0).department()).isEqualTo(new CourseResponse.Department(7L, "컴퓨터공학전공"));
		assertThat(page.items().get(1).department()).isNull();
	}

	@Test
	void 음수_페이지는_거부한다() {
		assertThat(thrownBy(() -> service.search(USER_ID, null, null, -1, null)).getErrorCode())
			.isEqualTo(ErrorCode.INVALID_INPUT);
	}

}
