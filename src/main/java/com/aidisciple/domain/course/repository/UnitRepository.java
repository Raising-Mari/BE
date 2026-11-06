package com.aidisciple.domain.course.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.course.entity.Unit;

public interface UnitRepository extends JpaRepository<Unit, Long> {

	List<Unit> findByCourseId(Long courseId);

	Optional<Unit> findByCourseIdAndNormalizedTitle(Long courseId, String normalizedTitle);

}
