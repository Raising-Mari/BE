package com.aidisciple.domain.material.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.material.dto.MaterialUnitsResponse;
import com.aidisciple.domain.material.dto.UpdateMaterialUnitsRequest;
import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.event.MaterialUnitsChangedEvent;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * M-06. 자료가 출처가 될 주차를 통째로 바꾼다. 연결 자체만 저장하고, 문항을 만들거나 없애는 일은 문제 은행(WP7)이
 * {@link MaterialUnitsChangedEvent} 를 받아 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialUnitService {

	private final MaterialQueryService materialQueryService;
	private final MaterialUnitRepository materialUnitRepository;
	private final UnitService unitService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public MaterialUnitsResponse replace(long userId, long materialId, UpdateMaterialUnitsRequest request) {
		Material material = materialQueryService.requireOwned(userId, materialId);

		Set<Long> wanted = new TreeSet<>(request.unitIds());
		unitService.requireAllInCourse(material.getCourseId(), wanted);

		Set<Long> current = new HashSet<>();
		materialUnitRepository.findByIdMaterialId(materialId).forEach(link -> current.add(link.getUnitId()));

		List<Long> added = wanted.stream().filter(unitId -> !current.contains(unitId)).toList();
		List<Long> removed = current.stream().filter(unitId -> !wanted.contains(unitId)).sorted().toList();

		try {
			materialUnitRepository.deleteAllById(removed.stream().map(unitId -> new MaterialUnit.Key(materialId, unitId)).toList());
			materialUnitRepository.saveAll(added.stream().map(unitId -> MaterialUnit.link(materialId, unitId)).toList());
			materialUnitRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// 같은 자료의 연결을 동시에 바꾼 두 요청이 겹친 경우
			throw new BusinessException(ErrorCode.CONFLICT);
		}

		if (!added.isEmpty() || !removed.isEmpty()) {
			eventPublisher.publishEvent(new MaterialUnitsChangedEvent(materialId, material.getCourseId(),
				material.getStatus() == MaterialStatus.DONE, added, removed));
		}
		return new MaterialUnitsResponse(materialId, List.copyOf(wanted));
	}

}
