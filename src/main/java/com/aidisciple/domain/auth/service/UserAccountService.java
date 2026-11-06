package com.aidisciple.domain.auth.service;

import java.time.Clock;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aidisciple.domain.auth.dto.LoginResponse.LoginUser;
import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 로그인 과정에서 DB 를 쓰는 짧은 작업만 모았다. Google 토큰 검증 같은 외부 호출은
 * {@link GoogleLoginService} 가 트랜잭션 밖에서 끝낸 뒤 이 서비스를 부른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService {

	private final UserRepository userRepository;
	private final SchoolRepository schoolRepository;
	private final Clock clock;

	public Optional<School> findSchoolByEmailDomain(String hostedDomain) {
		if (!StringUtils.hasText(hostedDomain)) {
			return Optional.empty();
		}
		return schoolRepository.findByEmailDomain(hostedDomain.strip());
	}

	/** 이미 가입한 사람이면 마지막 로그인 시각을 갱신하고 돌려준다. 가입 전이면 비어 있다. */
	@Transactional
	public Optional<LoginUser> recordLogin(String googleSub) {
		Optional<User> found = userRepository.findByGoogleSub(googleSub);
		if (found.isPresent() && !found.get().isActive()) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "탈퇴한 계정입니다.");
		}
		return found.map(user -> {
			user.markLoggedIn(clock.instant());
			return new LoginUser(user.getId(), user.getNickname(), user.getRole());
		});
	}

}
