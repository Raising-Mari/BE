package com.aidisciple.domain.auth.service;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.config.AdminProperties;
import com.aidisciple.domain.auth.dto.LoginResponse.LoginUser;
import com.aidisciple.domain.auth.dto.NicknameAvailabilityResponse;
import com.aidisciple.domain.auth.dto.SignupRequest;
import com.aidisciple.domain.auth.dto.SignupResponse;
import com.aidisciple.domain.auth.entity.User;
import com.aidisciple.domain.auth.logic.NicknamePolicy;
import com.aidisciple.domain.auth.repository.DepartmentRepository;
import com.aidisciple.domain.auth.repository.UserRepository;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.auth.security.SignupClaims;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.security.UserRole;

/** A-03 가입, A-04 닉네임 중복 확인. 외부 호출이 없어서 쓰기 메서드 전체를 한 트랜잭션으로 묶는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupService {

	private final UserRepository userRepository;
	private final DepartmentRepository departmentRepository;
	private final JwtProvider jwtProvider;
	private final AdminProperties adminProperties;
	private final Clock clock;

	/** 형식이 틀린 닉네임은 오류가 아니라 "쓸 수 없음"으로 답한다. 입력하는 동안 부르는 API 이기 때문이다. */
	public NicknameAvailabilityResponse nicknameAvailability(String nickname) {
		String stripped = nickname == null ? "" : nickname.strip();
		return new NicknameAvailabilityResponse(NicknamePolicy.isValid(stripped) && !userRepository.existsByNickname(stripped));
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		SignupClaims claims = jwtProvider.parseSignupToken(request.signupToken())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_SIGNUP_TOKEN_INVALID));
		if (!request.agreedTerms()) {
			throw new BusinessException(ErrorCode.AUTH_TERMS_REQUIRED);
		}
		String nickname = request.nickname().strip();
		if (!NicknamePolicy.isValid(nickname)) {
			throw new BusinessException(ErrorCode.AUTH_NICKNAME_INVALID);
		}
		// 다른 학교의 학과를 고르게 두지 않는다. 가입 토큰이 알려 준 학교만 허용한다.
		departmentRepository.findById(request.departmentId())
			.filter(department -> department.getSchoolId().equals(claims.schoolId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 학과입니다."));

		// 가입 토큰은 1회용이다: 이미 가입한 계정이면 같은 토큰으로 다시 가입할 수 없다.
		if (userRepository.existsByGoogleSub(claims.googleSub())) {
			throw new BusinessException(ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);
		}
		if (userRepository.existsByNickname(nickname)) {
			throw new BusinessException(ErrorCode.AUTH_NICKNAME_TAKEN);
		}

		UserRole role = adminProperties.isAdmin(claims.email()) ? UserRole.ADMIN : UserRole.STUDENT;
		User user = User.create(claims.schoolId(), request.departmentId(), claims.googleSub(), claims.email(), nickname,
			role, clock.instant());
		user.markLoggedIn(clock.instant());
		save(user);

		LoginUser loginUser = new LoginUser(user.getId(), user.getNickname(), user.getRole());
		return new SignupResponse(jwtProvider.issueAccessToken(user.getId(), user.getRole()), loginUser);
	}

	/** 위의 중복 검사를 동시에 통과한 두 요청은 DB 의 UNIQUE 제약이 마지막으로 막는다. */
	private void save(User user) {
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			String message = String.valueOf(e.getMostSpecificCause().getMessage());
			if (message.contains("uk_users_nickname")) {
				throw new BusinessException(ErrorCode.AUTH_NICKNAME_TAKEN);
			}
			if (message.contains("uk_users_google_sub")) {
				throw new BusinessException(ErrorCode.AUTH_SIGNUP_TOKEN_INVALID);
			}
			throw new BusinessException(ErrorCode.CONFLICT);
		}
	}

}
