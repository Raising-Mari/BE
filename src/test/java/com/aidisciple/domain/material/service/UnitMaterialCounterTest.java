package com.aidisciple.domain.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;

class UnitMaterialCounterTest {

	private final MaterialUnitRepository materialUnitRepository = mock(MaterialUnitRepository.class);
	private final MaterialRepository materialRepository = mock(MaterialRepository.class);
	private final UnitMaterialCounter counter = new UnitMaterialCounter(materialUnitRepository, materialRepository);

	private static Material material(long id) {
		Material material = Material.accept(5L, 42L, "자료", MaterialSourceType.PDF, "a.pdf", 10L, Instant.parse("2026-11-09T00:00:00Z"));
		ReflectionTestUtils.setField(material, "id", id);
		return material;
	}

	@Test
	void DONE_자료만_주차별로_센다() {
		when(materialUnitRepository.findByIdUnitIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
			MaterialUnit.link(10L, 1L), MaterialUnit.link(11L, 1L), MaterialUnit.link(11L, 2L), MaterialUnit.link(12L, 2L)));
		// 12번 자료는 처리 중이거나 실패라 DONE 조회에서 빠진다
		when(materialRepository.findByIdInAndStatusOrderByIdAsc(Set.of(10L, 11L, 12L), MaterialStatus.DONE))
			.thenReturn(List.of(material(10L), material(11L)));

		assertThat(counter.doneMaterialCounts(List.of(1L, 2L, 3L))).isEqualTo(Map.of(1L, 2L, 2L, 1L));
	}

	@Test
	void 연결된_자료가_없으면_자료를_조회하지_않고_빈_맵이다() {
		when(materialUnitRepository.findByIdUnitIdIn(List.of(1L))).thenReturn(List.of());

		assertThat(counter.doneMaterialCounts(List.of(1L))).isEmpty();
		verify(materialRepository, never()).findByIdInAndStatusOrderByIdAsc(any(), any());
	}

	@Test
	void 주차가_없으면_아무것도_조회하지_않는다() {
		assertThat(counter.doneMaterialCounts(List.of())).isEmpty();
		verify(materialUnitRepository, never()).findByIdUnitIdIn(any());
	}

}
