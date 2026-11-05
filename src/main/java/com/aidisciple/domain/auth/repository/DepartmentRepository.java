package com.aidisciple.domain.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.auth.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

	/** 시드 순서(대학 목록 순서)가 사람이 읽기 좋은 순서라서 id 순으로 준다. */
	List<Department> findAllByOrderByIdAsc();

}
