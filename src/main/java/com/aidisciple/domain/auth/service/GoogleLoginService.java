package com.aidisciple.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aidisciple.domain.auth.dto.GoogleLoginResponse;
import com.aidisciple.domain.auth.dto.LoginResponse.LoginUser;
import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.logic.EmailMasker;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

/**
 * A-01. 클래스에 트랜잭션을 걸지 않는다: Google 서버를 부르는 동안 DB 커넥션을 쥐지 않기 위해서다.
 * DB 를 쓰는 부분은 {@link UserAccountService} 가 각자 짧은 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

	static final String RATE_LIMIT_POLICY = "auth.google-login";

	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;
	private final GoogleIdentityVerifier googleIdentityVerifier;
	private final UserAccountService userAccountService;
	private final JwtProvider jwtProvider;

	public GoogleLoginResponse login(String idToken, String clientIp) {
		rateLimiter.check(RATE_LIMIT_POLICY, rateLimitProperties.googleLogin(), clientIp);

		GoogleIdentity identity = googleIdentityVerifier.verify(idToken);
		if (!identity.emailVerified() || !StringUtils.hasText(identity.subject()) || !StringUtils.hasText(identity.email())) {
			throw new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
		}
		// 보안은 이 서버 검사가 맡는다. FE 가 Google 로그인 창에 hd 를 힌트로 넘기는 것은 편의일 뿐이다.
		School school = userAccountService.findSchoolByEmailDomain(identity.hostedDomain())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED));

		return userAccountService.recordLogin(identity.subject())
			.map(this::registered)
			.orElseGet(() -> notRegistered(identity, school));
	}

	private GoogleLoginResponse registered(LoginUser user) {
		return new GoogleLoginResponse(true, jwtProvider.issueAccessToken(user.id(), user.role()), user, null, null);
	}

	private GoogleLoginResponse notRegistered(GoogleIdentity identity, School school) {
		String signupToken = jwtProvider.issueSignupToken(identity.subject(), identity.email(), school.getId());
		return new GoogleLoginResponse(false, null, null, signupToken,
			new GoogleLoginResponse.SignupProfile(EmailMasker.mask(identity.email())));
	}

}
