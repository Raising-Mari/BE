package com.aidisciple.domain.auth.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.dto.UserSummary;
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

	/**
	 * {@link #requireActive} 와 같지만 사용자 행을 잠근다. 같은 사용자의 요청이 겹치면(방 만들기 두 번 클릭 등)
	 * 한 줄로 서서 "진행 중 배틀은 하나" 같은 확인이 함께 통과하지 않는다. 쓰기 트랜잭션 안에서만 부른다.
	 */
	@Transactional
	public UserRef lockActive(long userId) {
		return userRepository.findLockedById(userId)
			.filter(User::isActive)
			.map(user -> new UserRef(user.getId(), user.getSchoolId(), user.getDepartmentId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	/** 화면에 보여 줄 사용자 요약(닉네임·학과). 없는 사용자는 결과에 없다. */
	public Map<Long, UserSummary> summaries(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(userIds).stream()
			.collect(Collectors.toMap(User::getId, user -> new UserSummary(user.getId(), user.getNickname(), user.getDepartmentId())));
	}

	/** 그 학과의 활성 사용자 id */
	public List<Long> activeUserIdsInDepartment(long departmentId) {
		return userRepository.findByDepartmentId(departmentId).stream()
			.filter(User::isActive)
			.map(User::getId)
			.toList();
	}

	/** 화면에 보여 줄 닉네임. 없는 사용자는 결과에 없다. */
	public Map<Long, String> nicknames(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(userIds).stream()
			.collect(Collectors.toMap(User::getId, User::getNickname));
	}

}
