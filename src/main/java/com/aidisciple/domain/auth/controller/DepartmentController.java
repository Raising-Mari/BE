package com.aidisciple.domain.auth.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.auth.dto.DepartmentResponse;
import com.aidisciple.domain.auth.service.DepartmentService;

/** 가입 전에 부르는 API 라서 SecurityConfig 의 공개 경로에 {@code /api/departments} 를 넣었다. */
@Tag(name = "인증")
@RestController
@RequiredArgsConstructor
public class DepartmentController {

	private final DepartmentService departmentService;

	@Operation(summary = "A-05 학과 목록 (단과대 포함, 가입 화면용)")
	@GetMapping("/api/departments")
	public List<DepartmentResponse> departments() {
		return departmentService.list();
	}

}
