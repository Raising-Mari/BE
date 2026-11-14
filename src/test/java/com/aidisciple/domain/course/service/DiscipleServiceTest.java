package com.aidisciple.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.CreateDiscipleRequest;
import com.aidisciple.domain.course.dto.DiscipleResponse;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class DiscipleServiceTest {

	private static final long USER_ID = 42L;
	private static final long COURSE_ID = 5L;

	private final DiscipleRepository discipleRepository = mock(DiscipleRepository.class);
	private final CourseService courseService = mock(CourseService.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final DiscipleService service = new DiscipleService(discipleRepository, courseService, userLookupService);

	private final UserRef user = new UserRef(USER_ID, 1L, null);

	@BeforeEach
	void 기본_상태() {
		Course course = Course.create(1L, null, "자료구조", "자료구조", "2026-2", 1L);
		ReflectionTestUtils.setField(course, "id", COURSE_ID);
		when(userLookupService.requireActive(USER_ID)).thenReturn(user);
		when(courseService.requireVisible(COURSE_ID, user)).thenReturn(course);
		when(discipleRepository.saveAndFlush(any(Disciple.class))).thenAnswer(invocation -> {
			Disciple saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 9L);   // 실제로는 DB 가 정하는 값
			return saved;
		});
	}

	private static BusinessException thrownBy(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	@Test
	void 제자를_만들면_내_제자로_과목에_등록된다() {
		DiscipleResponse response = service.create(USER_ID, COURSE_ID, new CreateDiscipleRequest("  마리  ", "cat"));

		ArgumentCaptor<Disciple> saved = ArgumentCaptor.forClass(Disciple.class);
		verify(discipleRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getCourseId()).isEqualTo(COURSE_ID);
		assertThat(saved.getValue().getName()).isEqualTo("마리");
		assertThat(saved.getValue().getCharacterKey()).isEqualTo("cat");
		assertThat(response.id()).isEqualTo(9L);
		assertThat(response.courseId()).isEqualTo(COURSE_ID);
		assertThat(response.name()).isEqualTo("마리");
	}

	@Test
	void 캐릭터를_고르지_않으면_null로_저장한다() {
		service.create(USER_ID, COURSE_ID, new CreateDiscipleRequest("마리", null));
		service.create(USER_ID, COURSE_ID, new CreateDiscipleRequest("마리", "  "));

		ArgumentCaptor<Disciple> saved = ArgumentCaptor.forClass(Disciple.class);
		verify(discipleRepository, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
		assertThat(saved.getAllValues()).extracting(Disciple::getCharacterKey).containsOnlyNulls();
	}

	@Test
	void 이미_제자가_있으면_만들_수_없다() {
		when(discipleRepository.existsByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(true);

		assertThat(thrownBy(() -> service.create(USER_ID, COURSE_ID, new CreateDiscipleRequest("마리", null))).getErrorCode())
			.isEqualTo(ErrorCode.DISCIPLE_ALREADY_EXISTS);
		verify(discipleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 동시에_두_번_눌러_DB_UNIQUE에_걸리면_이미_있다고_답한다() {
		when(discipleRepository.saveAndFlush(any(Disciple.class))).thenThrow(new DataIntegrityViolationException("dup"));

		assertThat(thrownBy(() -> service.create(USER_ID, COURSE_ID, new CreateDiscipleRequest("마리", null))).getErrorCode())
			.isEqualTo(ErrorCode.DISCIPLE_ALREADY_EXISTS);
	}

	@Test
	void 볼_수_없는_과목에는_제자를_만들_수_없다() {
		when(courseService.requireVisible(6L, user)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(thrownBy(() -> service.create(USER_ID, 6L, new CreateDiscipleRequest("마리", null))).getErrorCode())
			.isEqualTo(ErrorCode.NOT_FOUND);
		verify(discipleRepository, never()).saveAndFlush(any());
	}

	@Test
	void 내_제자를_조회한다() {
		Disciple disciple = Disciple.create(USER_ID, COURSE_ID, "마리", "cat");
		ReflectionTestUtils.setField(disciple, "id", 9L);
		when(discipleRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.of(disciple));

		DiscipleResponse response = service.mine(USER_ID, COURSE_ID);

		assertThat(response.id()).isEqualTo(9L);
		assertThat(response.name()).isEqualTo("마리");
		assertThat(response.characterKey()).isEqualTo("cat");
	}

	@Test
	void 제자가_없으면_DISCIPLE_NOT_FOUND다() {
		when(discipleRepository.findByUserIdAndCourseId(USER_ID, COURSE_ID)).thenReturn(Optional.empty());

		assertThat(thrownBy(() -> service.mine(USER_ID, COURSE_ID)).getErrorCode()).isEqualTo(ErrorCode.DISCIPLE_NOT_FOUND);
	}

	@Test
	void 볼_수_없는_과목의_제자는_과목이_없다고_답한다() {
		when(courseService.requireVisible(6L, user)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(thrownBy(() -> service.mine(USER_ID, 6L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

}
