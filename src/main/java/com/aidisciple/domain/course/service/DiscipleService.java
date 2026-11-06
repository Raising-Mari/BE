package com.aidisciple.domain.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.dto.CreateDiscipleRequest;
import com.aidisciple.domain.course.dto.DiscipleResponse;
import com.aidisciple.domain.course.entity.Course;
import com.aidisciple.domain.course.entity.Disciple;
import com.aidisciple.domain.course.repository.DiscipleRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** D-01·D-02. 제자는 과목마다 한 명이고, 만드는 것이 곧 그 과목의 수강 등록이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscipleService {

	private final DiscipleRepository discipleRepository;
	private final CourseService courseService;
	private final UserLookupService userLookupService;

	@Transactional
	public DiscipleResponse create(long userId, long courseId, CreateDiscipleRequest request) {
		UserRef user = userLookupService.requireActive(userId);
		Course course = courseService.requireVisible(courseId, user);

		if (discipleRepository.existsByUserIdAndCourseId(userId, course.getId())) {
			throw new BusinessException(ErrorCode.DISCIPLE_ALREADY_EXISTS);
		}
		String characterKey = request.characterKey() == null || request.characterKey().isBlank()
			? null : request.characterKey().strip();
		try {
			return toResponse(discipleRepository.saveAndFlush(
				Disciple.create(userId, course.getId(), request.name().strip(), characterKey)));
		} catch (DataIntegrityViolationException e) {
			// 동시에 두 번 눌러 확인을 함께 통과한 경우: UNIQUE(user_id, course_id) 가 막아 준다.
			throw new BusinessException(ErrorCode.DISCIPLE_ALREADY_EXISTS);
		}
	}

	public DiscipleResponse mine(long userId, long courseId) {
		UserRef user = userLookupService.requireActive(userId);
		Course course = courseService.requireVisible(courseId, user);
		return discipleRepository.findByUserIdAndCourseId(userId, course.getId())
			.map(DiscipleService::toResponse)
			.orElseThrow(() -> new BusinessException(ErrorCode.DISCIPLE_NOT_FOUND));
	}

	private static DiscipleResponse toResponse(Disciple disciple) {
		return new DiscipleResponse(disciple.getId(), disciple.getCourseId(), disciple.getName(),
			disciple.getCharacterKey(), disciple.getCreatedAt());
	}

}
