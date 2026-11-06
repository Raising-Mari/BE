package com.aidisciple.domain.course.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/**
	 * C-05. 숫자를 값으로 비교한 순서로 준다("2주차"가 "10주차"보다 앞).
	 * 자료 수(WP5)·내 노트 수(WP6)·배틀 가능 여부(WP7)는 그 기능이 생길 때까지 만들어질 수 없는 값이라 지금은 0, 0, false 다.
	 * 각 기능이 들어올 때 해당 도메인의 서비스를 불러 채운다.
	 */
	public List<UnitResponse> list(long userId, long courseId) {
		UserRef user = userLookupService.requireActive(userId);
		Course course = courseService.requireVisible(courseId, user);
		return unitRepository.findByCourseId(course.getId()).stream()
			.sorted(Comparator.comparing(Unit::getTitle, UnitTitleOrder::compare).thenComparing(Unit::getId))
			.map(UnitService::toResponse)
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
			return toResponse(unitRepository.saveAndFlush(Unit.create(course.getId(), title, normalizedTitle, userId)));
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

	private static UnitResponse toResponse(Unit unit) {
		return new UnitResponse(unit.getId(), unit.getTitle(), 0, 0, false);
	}

}
