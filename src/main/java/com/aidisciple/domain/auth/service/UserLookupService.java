package com.aidisciple.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 다른 도메인(과목·배틀 등)이 사용자 정보를 읽는 유일한 통로. 다른 도메인은 {@code UserRepository} 나 {@code User} 엔티티를 직접 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLookupService {

	private final UserRepository userRepository;

	/** 액세스 토큰은 유효한데 사용자가 없거나 탈퇴했다면 로그인이 풀린 것으로 본다. */
	public UserRef requireActive(long userId) {
		return userRepository.findById(userId)
			.filter(User::isActive)
			.map(user -> new UserRef(user.getId(), user.getSchoolId(), user.getDepartmentId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

}
