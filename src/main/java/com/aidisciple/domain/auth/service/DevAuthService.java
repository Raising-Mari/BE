package com.aidisciple.domain.auth.service;

import java.time.Clock;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.dto.GoogleLoginResponse;
import com.aidisciple.domain.auth.dto.LoginResponse.LoginUser;
import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.logic.EmailMasker;
import com.aidisciple.domain.auth.repository.SchoolRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * Google 없이 개발할 수 있게 이메일만으로 로그인시킨다. {@code local} 프로필에만 빈이 만들어진다.
 * 가입한 계정이면 A-01 과 같이 액세스 토큰을, 아니면 가입 토큰을 줘서 가입 화면까지 Google 없이 시험할 수 있다.
 */
@Service
@Profile("local")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DevAuthService {

	private static final String SCHOOL_CODE = "MJU";
	private static final String DEV_SUB_PREFIX = "dev:";

	private final UserRepository userRepository;
	private final SchoolRepository schoolRepository;
	private final JwtProvider jwtProvider;
	private final Clock clock;

	@Transactional
	public GoogleLoginResponse login(String email) {
		String normalized = email.strip().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(normalized).orElse(null);
		if (user == null) {
			return signupRequired(normalized);
		}
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		user.markLoggedIn(clock.instant());
		LoginUser loginUser = new LoginUser(user.getId(), user.getNickname(), user.getRole());
		return new GoogleLoginResponse(true, jwtProvider.issueAccessToken(user.getId(), user.getRole()), loginUser, null, null);
	}

	private GoogleLoginResponse signupRequired(String email) {
		School school = schoolRepository.findByCode(SCHOOL_CODE)
			.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
		String signupToken = jwtProvider.issueSignupToken(DEV_SUB_PREFIX + email, email, school.getId());
		return new GoogleLoginResponse(false, null, null, signupToken,
			new GoogleLoginResponse.SignupProfile(EmailMasker.mask(email)));
	}

}
