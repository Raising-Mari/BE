package com.aidisciple.domain.material.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.course.service.UnitMaterialCountLookup;
import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;

/** C-05 {@code materialCount} 의 구현. 주차에 연결된 DONE 자료를 센다(올린 사람은 가리지 않고 개수만 낸다). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitMaterialCounter implements UnitMaterialCountLookup {

	private final MaterialUnitRepository materialUnitRepository;
	private final MaterialRepository materialRepository;

	@Override
	public Map<Long, Long> doneMaterialCounts(Collection<Long> unitIds) {
		if (unitIds.isEmpty()) {
			return Map.of();
		}
		List<MaterialUnit> links = materialUnitRepository.findByIdUnitIdIn(unitIds);
		if (links.isEmpty()) {
			return Map.of();
		}
		Set<Long> doneIds = materialRepository
			.findByIdInAndStatusOrderByIdAsc(links.stream().map(MaterialUnit::getMaterialId).collect(Collectors.toSet()),
				MaterialStatus.DONE)
			.stream().map(Material::getId).collect(Collectors.toSet());
		return links.stream()
			.filter(link -> doneIds.contains(link.getMaterialId()))
			.collect(Collectors.groupingBy(MaterialUnit::getUnitId, Collectors.counting()));
	}

}
