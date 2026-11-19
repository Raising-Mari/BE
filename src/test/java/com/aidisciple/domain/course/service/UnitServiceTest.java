package com.aidisciple.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.CreateUnitRequest;
import com.aidisciple.domain.course.dto.UnitResponse;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class UnitServiceTest {

	private static final long USER_ID = 42L;
	private static final long COURSE_ID = 5L;

	private final UnitRepository unitRepository = mock(UnitRepository.class);
	private final CourseService courseService = mock(CourseService.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final UnitNoteCountLookup noteCountLookup = mock(UnitNoteCountLookup.class);
	private final UnitMaterialCountLookup materialCountLookup = mock(UnitMaterialCountLookup.class);
	private final UnitBattleReadyLookup battleReadyLookup = mock(UnitBattleReadyLookup.class);
	private final UnitService service = new UnitService(unitRepository, courseService, userLookupService,
		noteCountLookup, materialCountLookup, battleReadyLookup);

	private final UserRef user = new UserRef(USER_ID, 1L, null);

	@BeforeEach
	void 기본_상태() {
		Course course = Course.create(1L, null, "자료구조", "자료구조", "2026-2", 1L);
		ReflectionTestUtils.setField(course, "id", COURSE_ID);
		when(userLookupService.requireActive(USER_ID)).thenReturn(user);
		when(courseService.requireVisible(COURSE_ID, user)).thenReturn(course);
		when(unitRepository.saveAndFlush(any(Unit.class))).thenAnswer(invocation -> {
			Unit saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 77L);   // 실제로는 DB 가 정하는 값
			return saved;
		});
	}

	private static Unit unit(long id, String title) {
		Unit unit = Unit.create(COURSE_ID, title, title.replace(" ", ""), 1L);
		ReflectionTestUtils.setField(unit, "id", id);
		return unit;
	}

	private static BusinessException thrownBy(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	// --- C-05 ---

	@Test
	void 주차_목록은_숫자를_값으로_비교한_순서로_준다() {
		when(unitRepository.findByCourseId(COURSE_ID)).thenReturn(List.of(
			unit(1, "10주차"), unit(2, "2주차"), unit(3, "1주차"), unit(4, "중간고사"), unit(5, "11주차")));

		List<UnitResponse> units = service.list(USER_ID, COURSE_ID);

		assertThat(units).extracting(UnitResponse::title).containsExactly("1주차", "2주차", "10주차", "11주차", "중간고사");
	}

	@Test
	void 자료_수_내_노트_수_배틀_가능_여부를_주차마다_채운다() {
		when(unitRepository.findByCourseId(COURSE_ID)).thenReturn(List.of(unit(1, "1주차"), unit(2, "2주차"), unit(3, "3주차")));
		when(noteCountLookup.activeNoteCounts(USER_ID, COURSE_ID)).thenReturn(Map.of(1L, 4L, 3L, 1L));
		when(materialCountLookup.doneMaterialCounts(List.of(1L, 2L, 3L))).thenReturn(Map.of(1L, 2L));
		when(battleReadyLookup.battleReadyUnits(USER_ID, List.of(1L, 2L, 3L), UnitService.BATTLE_READY_QUESTION_COUNT))
			.thenReturn(Set.of(1L, 2L));

		List<UnitResponse> units = service.list(USER_ID, COURSE_ID);

		assertThat(units).extracting(UnitResponse::id, UnitResponse::materialCount, UnitResponse::myNoteCount, UnitResponse::battleReady)
			.containsExactly(
				tuple(1L, 2L, 4L, true),
				tuple(2L, 0L, 0L, true),   // 값이 없는 주차는 0
				tuple(3L, 0L, 1L, false));
	}

	@Test
	void 조회_결과가_없으면_0_0_false다() {
		when(unitRepository.findByCourseId(COURSE_ID)).thenReturn(List.of(unit(1, "1주차")));
		when(noteCountLookup.activeNoteCounts(USER_ID, COURSE_ID)).thenReturn(Map.of());
		when(materialCountLookup.doneMaterialCounts(List.of(1L))).thenReturn(Map.of());
		when(battleReadyLookup.battleReadyUnits(USER_ID, List.of(1L), UnitService.BATTLE_READY_QUESTION_COUNT)).thenReturn(Set.of());

		UnitResponse response = service.list(USER_ID, COURSE_ID).get(0);

		assertThat(response.materialCount()).isZero();
		assertThat(response.myNoteCount()).isZero();
		assertThat(response.battleReady()).isFalse();
	}

	@Test
	void 목록은_정렬한_순서로_주고_조회도_그_주차들에_대해서만_한다() {
		when(unitRepository.findByCourseId(COURSE_ID)).thenReturn(List.of(unit(1, "10주차"), unit(2, "2주차")));
		when(noteCountLookup.activeNoteCounts(USER_ID, COURSE_ID)).thenReturn(Map.of(1L, 7L));

		List<UnitResponse> units = service.list(USER_ID, COURSE_ID);

		assertThat(units).extracting(UnitResponse::id).containsExactly(2L, 1L);
		assertThat(units).extracting(UnitResponse::myNoteCount).containsExactly(0L, 7L);   // 노트 수가 id 로 맞게 붙는다
		verify(materialCountLookup).doneMaterialCounts(List.of(2L, 1L));
	}

	@Test
	void 주차가_없으면_빈_목록이고_다른_도메인을_부르지_않는다() {
		when(unitRepository.findByCourseId(COURSE_ID)).thenReturn(List.of());

		assertThat(service.list(USER_ID, COURSE_ID)).isEmpty();
		verify(noteCountLookup, never()).activeNoteCounts(anyLong(), anyLong());
		verify(materialCountLookup, never()).doneMaterialCounts(any());
		verify(battleReadyLookup, never()).battleReadyUnits(anyLong(), any(), anyInt());
	}

	@Test
	void 볼_수_없는_과목의_주차_목록은_조회하지_않는다() {
		when(courseService.requireVisible(6L, user)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(thrownBy(() -> service.list(USER_ID, 6L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		verify(unitRepository, never()).findByCourseId(any());
	}

	// --- C-06 ---

	@Test
	void 주차를_만들면_이름의_앞뒤_공백을_자르고_정규화한_이름과_함께_저장한다() {
		UnitResponse response = service.create(USER_ID, COURSE_ID, new CreateUnitRequest("  3 주차  "));

		ArgumentCaptor<Unit> saved = ArgumentCaptor.forClass(Unit.class);
		verify(unitRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getTitle()).isEqualTo("3 주차");
		assertThat(saved.getValue().getNormalizedTitle()).isEqualTo("3주차");
		assertThat(saved.getValue().getCourseId()).isEqualTo(COURSE_ID);
		assertThat(saved.getValue().getCreatedBy()).isEqualTo(USER_ID);
		assertThat(response.id()).isEqualTo(77L);
		assertThat(response.title()).isEqualTo("3 주차");
	}

	@Test
	void 공백만_다른_같은_이름이면_기존_주차_id와_함께_거부한다() {
		when(unitRepository.findByCourseIdAndNormalizedTitle(COURSE_ID, "3주차")).thenReturn(Optional.of(unit(12, "3주차")));

		BusinessException e = thrownBy(() -> service.create(USER_ID, COURSE_ID, new CreateUnitRequest("3 주차")));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNIT_DUPLICATE);
		assertThat(e.getDetails()).containsEntry("existingUnitId", 12L);
		verify(unitRepository, never()).saveAndFlush(any());
	}

	@Test
	void 이름이_보이지_않는_문자뿐이면_거부한다() {
		BusinessException e = thrownBy(() -> service.create(USER_ID, COURSE_ID, new CreateUnitRequest("" + (char) 0x200B)));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
		verify(unitRepository, never()).saveAndFlush(any());
	}

	@Test
	void 동시에_같은_이름을_만들어_DB_UNIQUE에_걸리면_중복으로_답한다() {
		when(unitRepository.saveAndFlush(any(Unit.class))).thenThrow(new DataIntegrityViolationException("dup"));

		BusinessException e = thrownBy(() -> service.create(USER_ID, COURSE_ID, new CreateUnitRequest("3주차")));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNIT_DUPLICATE);
		assertThat(e.getDetails()).isEmpty();
	}

	@Test
	void 볼_수_없는_과목에는_주차를_만들_수_없다() {
		when(courseService.requireVisible(6L, user)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(thrownBy(() -> service.create(USER_ID, 6L, new CreateUnitRequest("1주차"))).getErrorCode())
			.isEqualTo(ErrorCode.NOT_FOUND);
		verify(unitRepository, never()).saveAndFlush(any());
	}

}
