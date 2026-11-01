package com.aidisciple.domain.auth.service;

import java.time.Clock;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.LoginResponse;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** Google 없이 개발할 수 있게 이메일만으로 로그인시킨다. {@code local} 프로필에만 빈이 만들어진다. */
@Service
@Profile("local")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevAuthService {

	private final UserRepository userRepository;
	private final JwtProvider jwtProvider;
	private final Clock clock;

	@Transactional
	public LoginResponse login(String email) {
		User user = userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "가입된 개발용 계정이 아닙니다."));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		user.markLoggedIn(clock.instant());
		return new LoginResponse(true, jwtProvider.issueAccessToken(user.getId(), user.getRole()),
			new LoginResponse.LoginUser(user.getId(), user.getNickname(), user.getRole()));
	}

}
