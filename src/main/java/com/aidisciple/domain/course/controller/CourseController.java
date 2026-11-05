package com.aidisciple.domain.course.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aidisciple.domain.course.dto.CourseResponse;
import com.aidisciple.domain.course.dto.CreateCourseRequest;
import com.aidisciple.domain.course.dto.CreateDiscipleRequest;
import com.aidisciple.domain.course.dto.CreateUnitRequest;
import com.aidisciple.domain.course.dto.DiscipleResponse;
import com.aidisciple.domain.course.dto.MyCourseResponse;
import com.aidisciple.domain.course.dto.UnitResponse;
import com.aidisciple.global.api.NotImplemented;
import com.aidisciple.global.api.PageResponse;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/**
 * 2절 과목·주차·제자 (WP4). 제자 만들기가 곧 수강 등록이고 경로도 과목 아래에 있어서 D-01·D-02 를 여기에 둔다.
 * 제자 엔티티를 어느 도메인에 둘지는 WP4 구현 때 정한다.
 */
@Tag(name = "과목·주차·제자")
@RestController
@RequestMapping("/api")
public class CourseController {

	@Operation(summary = "C-01 내 학교 과목 검색")
	@GetMapping("/courses")
	public PageResponse<CourseResponse> searchCourses(
		@CurrentUser AuthUser user,
		@RequestParam(required = false) String keyword,
		@RequestParam(required = false) Long departmentId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer size
	) {
		return NotImplemented.yet();
	}

	@Operation(summary = "C-02 과목 만들기 (같은 과목이 있으면 409 COURSE_DUPLICATE)")
	@PostMapping("/courses")
	@ResponseStatus(HttpStatus.CREATED)
	public CourseResponse createCourse(@CurrentUser AuthUser user, @Valid @RequestBody CreateCourseRequest request) {
		return NotImplemented.yet();
	}

	@Operation(summary = "C-03 내 과목 (제자가 있는 과목)")
	@GetMapping("/me/courses")
	public List<MyCourseResponse> myCourses(@CurrentUser AuthUser user) {
		return NotImplemented.yet();
	}

	@Operation(summary = "C-04 과목 상세")
	@GetMapping("/courses/{courseId}")
	public CourseResponse getCourse(@CurrentUser AuthUser user, @PathVariable Long courseId) {
		return NotImplemented.yet();
	}

	@Operation(summary = "C-05 주차 목록 + 내 현황")
	@GetMapping("/courses/{courseId}/units")
	public List<UnitResponse> units(@CurrentUser AuthUser user, @PathVariable Long courseId) {
		return NotImplemented.yet();
	}

	@Operation(summary = "C-06 주차 만들기 (같은 이름이 있으면 409 UNIT_DUPLICATE)")
	@PostMapping("/courses/{courseId}/units")
	@ResponseStatus(HttpStatus.CREATED)
	public UnitResponse createUnit(
		@CurrentUser AuthUser user,
		@PathVariable Long courseId,
		@Valid @RequestBody CreateUnitRequest request
	) {
		return NotImplemented.yet();
	}

	@Operation(summary = "D-01 제자 만들기 = 수강 등록 (이미 있으면 409 DISCIPLE_ALREADY_EXISTS)")
	@PostMapping("/courses/{courseId}/disciple")
	@ResponseStatus(HttpStatus.CREATED)
	public DiscipleResponse createDisciple(
		@CurrentUser AuthUser user,
		@PathVariable Long courseId,
		@Valid @RequestBody CreateDiscipleRequest request
	) {
		return NotImplemented.yet();
	}

	@Operation(summary = "D-02 내 제자")
	@GetMapping("/courses/{courseId}/disciple")
	public DiscipleResponse myDisciple(@CurrentUser AuthUser user, @PathVariable Long courseId) {
		return NotImplemented.yet();
	}

}
