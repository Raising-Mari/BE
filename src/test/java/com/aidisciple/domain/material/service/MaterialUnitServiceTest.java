package com.aidisciple.domain.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.material.dto.MaterialUnitsResponse;
import com.aidisciple.domain.material.dto.UpdateMaterialUnitsRequest;
import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.event.MaterialUnitsChangedEvent;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class MaterialUnitServiceTest {

	private static final long USER_ID = 42L;
	private static final long MATERIAL_ID = 8L;
	private static final long COURSE_ID = 5L;

	private final MaterialQueryService materialQueryService = mock(MaterialQueryService.class);
	private final MaterialUnitRepository materialUnitRepository = mock(MaterialUnitRepository.class);
	private final UnitService unitService = mock(UnitService.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final MaterialUnitService service = new MaterialUnitService(materialQueryService, materialUnitRepository,
		unitService, eventPublisher);

	private Material material;

	@BeforeEach
	void 기본_상태() {
		material = Material.accept(COURSE_ID, USER_ID, "자료", MaterialSourceType.TEXT, null, null, Instant.parse("2026-11-06T00:00:00Z"));
		ReflectionTestUtils.setField(material, "id", MATERIAL_ID);
		ReflectionTestUtils.setField(material, "status", MaterialStatus.DONE);
		when(materialQueryService.requireOwned(USER_ID, MATERIAL_ID)).thenReturn(material);
	}

	private void currentlyLinked(Long... unitIds) {
		when(materialUnitRepository.findByIdMaterialId(MATERIAL_ID))
			.thenReturn(java.util.Arrays.stream(unitIds).map(unitId -> MaterialUnit.link(MATERIAL_ID, unitId)).toList());
	}

	private static UpdateMaterialUnitsRequest request(Long... unitIds) {
		return new UpdateMaterialUnitsRequest(List.of(unitIds));
	}

	private MaterialUnitsChangedEvent publishedEvent() {
		ArgumentCaptor<MaterialUnitsChangedEvent> event = ArgumentCaptor.forClass(MaterialUnitsChangedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		return event.getValue();
	}

	@SuppressWarnings("unchecked")
	private List<MaterialUnit> savedLinks() {
		ArgumentCaptor<Iterable<MaterialUnit>> saved = ArgumentCaptor.forClass(Iterable.class);
		verify(materialUnitRepository).saveAll(saved.capture());
		return (List<MaterialUnit>) saved.getValue();
	}

	@Test
	void 새로_연결한_주차만_추가하고_빠진_주차만_푼다() {
		currentlyLinked(3L, 4L);

		MaterialUnitsResponse response = service.replace(USER_ID, MATERIAL_ID, request(4L, 6L, 5L));

		assertThat(response).isEqualTo(new MaterialUnitsResponse(MATERIAL_ID, List.of(4L, 5L, 6L)));
		assertThat(savedLinks()).extracting(MaterialUnit::getUnitId).containsExactly(5L, 6L);
		verify(materialUnitRepository).deleteAllById(List.of(new MaterialUnit.Key(MATERIAL_ID, 3L)));
	}

	@Test
	void 변경_내용을_이벤트로_알린다() {
		currentlyLinked(3L, 4L);

		service.replace(USER_ID, MATERIAL_ID, request(4L, 5L));

		MaterialUnitsChangedEvent event = publishedEvent();
		assertThat(event.materialId()).isEqualTo(MATERIAL_ID);
		assertThat(event.courseId()).isEqualTo(COURSE_ID);
		assertThat(event.materialDone()).isTrue();
		assertThat(event.addedUnitIds()).containsExactly(5L);
		assertThat(event.removedUnitIds()).containsExactly(3L);
	}

	@Test
	void 처리_중인_자료는_materialDone이_false로_알린다() {
		ReflectionTestUtils.setField(material, "status", MaterialStatus.PROCESSING);
		currentlyLinked();

		service.replace(USER_ID, MATERIAL_ID, request(3L));

		assertThat(publishedEvent().materialDone()).isFalse();
	}

	@Test
	void 빈_목록이면_연결을_모두_푼다() {
		currentlyLinked(3L, 4L);

		MaterialUnitsResponse response = service.replace(USER_ID, MATERIAL_ID, request());

		assertThat(response.unitIds()).isEmpty();
		assertThat(publishedEvent().removedUnitIds()).containsExactly(3L, 4L);
		assertThat(publishedEvent().addedUnitIds()).isEmpty();
	}

	@Test
	void 같은_주차를_여러_번_보내도_한_번만_연결한다() {
		currentlyLinked();

		MaterialUnitsResponse response = service.replace(USER_ID, MATERIAL_ID, request(3L, 3L, 3L));

		assertThat(response.unitIds()).containsExactly(3L);
		assertThat(savedLinks()).hasSize(1);
	}

	@Test
	void 바뀐_것이_없으면_이벤트를_내지_않는다() {
		currentlyLinked(3L, 4L);

		MaterialUnitsResponse response = service.replace(USER_ID, MATERIAL_ID, request(4L, 3L));

		assertThat(response.unitIds()).containsExactly(3L, 4L);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 이_과목의_주차가_아니면_아무것도_바꾸지_않는다() {
		currentlyLinked(3L);
		doThrow(new BusinessException(ErrorCode.UNIT_NOT_IN_COURSE)).when(unitService).requireAllInCourse(COURSE_ID, Set.of(3L, 99L));

		Throwable thrown = catchThrowable(() -> service.replace(USER_ID, MATERIAL_ID, request(3L, 99L)));

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNIT_NOT_IN_COURSE));
		verify(materialUnitRepository, never()).saveAll(anyIterable());
		verify(materialUnitRepository, never()).deleteAllById(anyIterable());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 올린_사람이_아니면_바꿀_수_없다() {
		when(materialQueryService.requireOwned(43L, MATERIAL_ID)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		Throwable thrown = catchThrowable(() -> service.replace(43L, MATERIAL_ID, request(3L)));

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(unitService, eventPublisher);
	}

	@Test
	void 동시에_같은_연결을_바꿔_DB에_걸리면_CONFLICT다() {
		currentlyLinked();
		when(materialUnitRepository.saveAll(anyIterable())).thenThrow(new DataIntegrityViolationException("dup"));

		Throwable thrown = catchThrowable(() -> service.replace(USER_ID, MATERIAL_ID, request(3L)));

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
		verifyNoInteractions(eventPublisher);
	}


}
