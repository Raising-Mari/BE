package com.aidisciple.domain.auth.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.DepartmentResponse;
import com.aidisciple.domain.auth.repository.DepartmentRepository;

/** A-05. 가입 전에 부르는 공개 API 라서 학교를 가리지 않고 시드된 전체 학과를 준다. 지금은 학교가 한 곳뿐이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

	private final DepartmentRepository departmentRepository;

	public List<DepartmentResponse> list() {
		return departmentRepository.findAllByOrderByIdAsc().stream()
			.map(department -> new DepartmentResponse(department.getId(), department.getName(), department.getCollegeName()))
			.toList();
	}

}
