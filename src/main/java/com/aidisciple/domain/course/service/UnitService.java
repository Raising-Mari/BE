package com.aidisciple.domain.course.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.aidisciple.domain.course.dto.UnitRef;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.CreateUnitRequest;
import com.aidisciple.domain.course.dto.UnitResponse;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Unit;
import com.aidisciple.domain.course.logic.NameNormalizer;
import com.aidisciple.domain.course.logic.UnitTitleOrder;
import com.aidisciple.domain.course.repository.UnitRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * C-05·C-06. 주차는 과목 안에서 모든 학생이 공유한다. 주차를 만드는 데 제자(수강 등록)가 필요하지는 않다:
 * 자료를 먼저 올리고 나서 주차를 고르거나 만들 수 있어야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnitService {

	private final UnitRepository unitRepository;
	private final CourseService courseService;
	private final UserLookupService userLookupService;
	private final UnitNoteCountLookup noteCountLookup;
	private final UnitMaterialCountLookup materialCountLookup;
	private final UnitBattleReadyLookup battleReadyLookup;

	/** battleReady 를 판단하는 문항 수. B-01 에서 문항 수를 생략했을 때의 기본값(5)과 같아야 한다. */
	static final int BATTLE_READY_QUESTION_COUNT = 5;

	/**
	 * C-05. 숫자를 값으로 비교한 순서로 준다("2주차"가 "10주차"보다 앞).
	 * 자료 수·내 노트 수·배틀 가능 여부는 각 도메인이 구현한 조회 인터페이스로 채운다(도메인 경계: 저장소를 직접 조회하지 않는다).
	 */
	public List<UnitResponse> list(long userId, long courseId) {
		UserRef user = userLookupService.requireActive(userId);
		Course course = courseService.requireVisible(courseId, user);
		List<Unit> units = unitRepository.findByCourseId(course.getId()).stream()
			.sorted(Comparator.comparing(Unit::getTitle, UnitTitleOrder::compare).thenComparing(Unit::getId))
			.toList();
		if (units.isEmpty()) {
			return List.of();
		}
		List<Long> unitIds = units.stream().map(Unit::getId).toList();
		Map<Long, Long> noteCounts = noteCountLookup.activeNoteCounts(userId, course.getId());
		Map<Long, Long> materialCounts = materialCountLookup.doneMaterialCounts(unitIds);
		Set<Long> battleReady = battleReadyLookup.battleReadyUnits(userId, unitIds, BATTLE_READY_QUESTION_COUNT);
		return units.stream()
			.map(unit -> new UnitResponse(unit.getId(), unit.getTitle(),
				materialCounts.getOrDefault(unit.getId(), 0L),
				noteCounts.getOrDefault(unit.getId(), 0L),
				battleReady.contains(unit.getId())))
			.toList();
	}

	@Transactional
	public UnitResponse create(long userId, long courseId, CreateUnitRequest request) {
		UserRef user = userLookupService.requireActive(userId);
		Course course = courseService.requireVisible(courseId, user);

		String title = request.title().strip();
		String normalizedTitle = NameNormalizer.normalize(title);
		if (normalizedTitle.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "주차 이름을 입력해 주세요.");
		}
		unitRepository.findByCourseIdAndNormalizedTitle(course.getId(), normalizedTitle)
			.ifPresent(existing -> {
				throw new BusinessException(ErrorCode.UNIT_DUPLICATE, Map.of("existingUnitId", existing.getId()));
			});

		try {
			return toNewUnitResponse(unitRepository.saveAndFlush(Unit.create(course.getId(), title, normalizedTitle, userId)));
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 이름을 만든 두 요청 중 늦은 쪽
			throw new BusinessException(ErrorCode.UNIT_DUPLICATE);
		}
	}

	/**
	 * 다른 도메인(자료 등)이 "이 주차들이 모두 이 과목 소속인가"를 확인할 때 쓴다. 하나라도 아니거나 없으면 UNIT_NOT_IN_COURSE.
	 */
	public void requireAllInCourse(long courseId, Collection<Long> unitIds) {
		if (unitIds.isEmpty()) {
			return;
		}
		Set<Long> requested = Set.copyOf(unitIds);
		if (unitRepository.findByCourseIdAndIdIn(courseId, requested).size() != requested.size()) {
			throw new BusinessException(ErrorCode.UNIT_NOT_IN_COURSE);
		}
	}

	/** 다른 도메인이 "이 주차가 이 과목 소속인가"를 확인하고 이름까지 받을 때 쓴다. 아니면 UNIT_NOT_IN_COURSE. */
	public UnitRef requireInCourse(long courseId, long unitId) {
		return unitRepository.findById(unitId)
			.filter(unit -> unit.getCourseId() == courseId)
			.map(UnitService::toRef)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNIT_NOT_IN_COURSE));
	}

	public Map<Long, UnitRef> findRefs(Collection<Long> unitIds) {
		if (unitIds.isEmpty()) {
			return Map.of();
		}
		return unitRepository.findAllById(unitIds).stream()
			.collect(Collectors.toMap(Unit::getId, UnitService::toRef));
	}

	private static UnitRef toRef(Unit unit) {
		return new UnitRef(unit.getId(), unit.getCourseId(), unit.getTitle());
	}

	/** 방금 만든 주차용 — 자료·노트·문항이 아직 있을 수 없으므로 0, 0, false 가 맞다. */
	private static UnitResponse toNewUnitResponse(Unit unit) {
		return new UnitResponse(unit.getId(), unit.getTitle(), 0, 0, false);
	}

}
