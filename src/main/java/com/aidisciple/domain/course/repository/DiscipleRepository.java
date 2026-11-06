package com.aidisciple.domain.course.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.course.entity.Disciple;

public interface DiscipleRepository extends JpaRepository<Disciple, Long> {

	Optional<Disciple> findByUserIdAndCourseId(Long userId, Long courseId);

	boolean existsByUserIdAndCourseId(Long userId, Long courseId);

	/** 최근에 만든 제자가 먼저 오도록 id 내림차순 */
	List<Disciple> findByUserIdOrderByIdDesc(Long userId);

	/** 검색 결과 한 페이지에서 "내 제자가 있는 과목"을 한 번에 가려내는 용도 */
	List<Disciple> findByUserIdAndCourseIdIn(Long userId, Collection<Long> courseIds);

}
