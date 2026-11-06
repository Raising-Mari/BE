package com.aidisciple.domain.course.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.course.entity.Unit;

public interface UnitRepository extends JpaRepository<Unit, Long> {

	List<Unit> findByCourseId(Long courseId);

	Optional<Unit> findByCourseIdAndNormalizedTitle(Long courseId, String normalizedTitle);

	/** 이 과목에 속한 주차만 골라낸다. 다른 과목의 주차 id 가 섞여 있으면 결과가 요청보다 적다. */
	List<Unit> findByCourseIdAndIdIn(Long courseId, Collection<Long> ids);

}
