package com.aidisciple.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.MeResponse;
import com.aidisciple.domain.auth.dto.UpdateMeRequest;
import com.aidisciple.domain.auth.entity.Department;
import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.logic.EmailMasker;
import com.aidisciple.domain.auth.logic.NicknamePolicy;
import com.aidisciple.domain.auth.repository.DepartmentRepository;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** A-06 내 정보, A-07 닉네임·학과 수정 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeService {

	private final UserRepository userRepository;
	private final SchoolRepository schoolRepository;
	private final DepartmentRepository departmentRepository;

	public MeResponse me(long userId) {
		return toResponse(activeUser(userId));
	}

	@Transactional
	public MeResponse update(long userId, UpdateMeRequest request) {
		if (request.nickname() == null && request.departmentId() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "바꿀 값을 하나 이상 보내 주세요.");
		}
		User user = activeUser(userId);

		if (request.nickname() != null) {
			String nickname = request.nickname().strip();
			if (!NicknamePolicy.isValid(nickname)) {
				throw new BusinessException(ErrorCode.AUTH_NICKNAME_INVALID);
			}
			boolean takenByOther = userRepository.findByNickname(nickname)
				.filter(other -> !other.getId().equals(user.getId()))
				.isPresent();
			if (takenByOther) {
				throw new BusinessException(ErrorCode.AUTH_NICKNAME_TAKEN);
			}
			user.changeNickname(nickname);
		}
		if (request.departmentId() != null) {
			departmentRepository.findById(request.departmentId())
				.filter(department -> department.getSchoolId().equals(user.getSchoolId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 학과입니다."));
			user.changeDepartment(request.departmentId());
		}

		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.AUTH_NICKNAME_TAKEN);
		}
		return toResponse(user);
	}

	/** 토큰은 유효한데 사용자가 없거나 탈퇴했다면 로그인이 풀린 것으로 본다. */
	private User activeUser(long userId) {
		return userRepository.findById(userId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private MeResponse toResponse(User user) {
		School school = schoolRepository.findById(user.getSchoolId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
		MeResponse.Department department = user.getDepartmentId() == null ? null
			: departmentRepository.findById(user.getDepartmentId()).map(MeService::toDepartment).orElse(null);
		return new MeResponse(user.getId(), user.getNickname(), EmailMasker.mask(user.getEmail()),
			new MeResponse.School(school.getId(), school.getName()), department, user.getRole());
	}

	private static MeResponse.Department toDepartment(Department department) {
		return new MeResponse.Department(department.getId(), department.getName(), department.getCollegeName());
	}

}
