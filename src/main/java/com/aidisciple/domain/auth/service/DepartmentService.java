package com.aidisciple.domain.auth.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.DepartmentResponse;
import com.aidisciple.domain.auth.entity.Department;
import com.aidisciple.domain.auth.repository.DepartmentRepository;

/** A-05. 가입 전에 부르는 공개 API 라서 학교를 가리지 않고 시드된 전체 학과를 준다. 지금은 학교가 한 곳뿐이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

	private final DepartmentRepository departmentRepository;

	public List<DepartmentResponse> list() {
		return departmentRepository.findAllByOrderByIdAsc().stream()
			.map(DepartmentService::toResponse)
			.toList();
	}

	/** 다른 도메인이 "이 학과가 이 학교 소속인가"를 확인할 때 쓴다. 없거나 다른 학교 학과면 비어 있다. */
	public Optional<DepartmentResponse> findInSchool(long departmentId, long schoolId) {
		return departmentRepository.findById(departmentId)
			.filter(department -> department.getSchoolId().equals(schoolId))
			.map(DepartmentService::toResponse);
	}

	/** 목록 응답에 학과 이름을 붙일 때 한 번에 읽는다. */
	public Map<Long, DepartmentResponse> findByIds(Collection<Long> departmentIds) {
		if (departmentIds.isEmpty()) {
			return Map.of();
		}
		return departmentRepository.findAllById(departmentIds).stream()
			.collect(Collectors.toMap(Department::getId, DepartmentService::toResponse, (a, b) -> a));
	}

	private static DepartmentResponse toResponse(Department department) {
		return new DepartmentResponse(department.getId(), department.getName(), department.getCollegeName());
	}

}
