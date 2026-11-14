package com.aidisciple.domain.course.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.aidisciple.domain.course.entity.Course;

/** 검색 조건이 선택적이라 {@link JpaSpecificationExecutor} 로 조건을 조립한다. (null 파라미터를 JPQL 에 넘기지 않기 위함) */
public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

	Optional<Course> findBySchoolIdAndNormalizedNameAndTerm(Long schoolId, String normalizedName, String term);

}
