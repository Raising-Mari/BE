package com.aidisciple.domain.material.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.material.dto.MaterialResponse;
import com.aidisciple.domain.material.dto.MaterialSummaryResponse;
import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** M-03·M-04. 자료는 올린 사람만 볼 수 있고, 남의 자료는 없는 자료와 똑같이 NOT_FOUND 다. 문장 내용은 어디에도 내려주지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialQueryService {

	private final MaterialRepository materialRepository;
	private final MaterialUnitRepository materialUnitRepository;
	private final UserLookupService userLookupService;
	private final CourseService courseService;

	/** 접수 직후부터 상태를 폴링하는 용도다. */
	public MaterialResponse get(long userId, long materialId) {
		Material material = requireOwned(userId, materialId);
		List<Long> unitIds = unitIdsOf(material.getId());
		return new MaterialResponse(material.getId(), material.getTitle(), material.getStatus(), material.getFailReason(),
			material.getSentenceCount(), unitIds);
	}

	/** 이 과목에 내가 올린 자료. 최근 것이 먼저 온다. */
	public List<MaterialSummaryResponse> mine(long userId, long courseId) {
		UserRef user = userLookupService.requireActive(userId);
		courseService.requireVisible(courseId, user);

		List<Material> materials = materialRepository
			.findByCourseIdAndUploaderIdAndStatusNotOrderByIdDesc(courseId, userId, MaterialStatus.DELETED);
		Map<Long, List<Long>> unitIdsByMaterial = materialUnitRepository
			.findByIdMaterialIdIn(materials.stream().map(Material::getId).toList()).stream()
			.collect(Collectors.groupingBy(MaterialUnit::getMaterialId,
				Collectors.mapping(MaterialUnit::getUnitId, Collectors.collectingAndThen(Collectors.toList(), MaterialQueryService::sorted))));

		return materials.stream()
			.map(material -> new MaterialSummaryResponse(material.getId(), material.getTitle(), material.getStatus(),
				material.getCreatedAt(), unitIdsByMaterial.getOrDefault(material.getId(), List.of())))
			.toList();
	}

	/** 올린 사람의 살아 있는 자료만 돌려준다. 주차 연결(M-06)도 같은 검사를 쓴다. */
	Material requireOwned(long userId, long materialId) {
		userLookupService.requireActive(userId);
		return materialRepository.findById(materialId)
			.filter(material -> material.isOwnedBy(userId) && material.isVisibleToOwner())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private List<Long> unitIdsOf(Long materialId) {
		return sorted(materialUnitRepository.findByIdMaterialId(materialId).stream().map(MaterialUnit::getUnitId).toList());
	}

	private static List<Long> sorted(Collection<Long> ids) {
		return ids.stream().sorted().toList();
	}

}
