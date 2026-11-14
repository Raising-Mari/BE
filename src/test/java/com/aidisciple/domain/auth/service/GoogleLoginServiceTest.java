package com.aidisciple.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.config.AuthProperties;
import com.aidisciple.domain.auth.dto.GoogleLoginResponse;
import com.aidisciple.domain.auth.dto.LoginResponse.LoginUser;
import com.aidisciple.domain.auth.entity.School;
import com.aidisciple.domain.auth.security.JwtProvider;
import com.aidisciple.domain.auth.security.SignupClaims;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.UserRole;

/** A-01 의 분기. Google 서버와 DB 는 가짜로 두고, 토큰 발급은 실제 {@link JwtProvider} 로 확인한다. */
class GoogleLoginServiceTest {

	private static final String IP = "203.0.113.7";

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);
	private final JwtProvider jwtProvider = new JwtProvider(
		new AuthProperties("client", "unit-test-secret-key-0123456789-abcdefghij", 7), clock);
	private final GoogleIdentityVerifier verifier = mock(GoogleIdentityVerifier.class);
	private final UserAccountService accounts = mock(UserAccountService.class);
	private final RateLimitProperties limits = new RateLimitProperties(
		new RateLimitProperties.Rule(10, Duration.ofMinutes(1)), null, null, null);
	private final GoogleLoginService service = new GoogleLoginService(
		new RateLimiter(clock), limits, verifier, accounts, jwtProvider);

	private static School school(long id) {
		School school = BeanUtils.instantiateClass(School.class);
		ReflectionTestUtils.setField(school, "id", id);
		return school;
	}

	private GoogleIdentity workspaceIdentity() {
		return new GoogleIdentity("sub-1", "abc@mju.ac.kr", true, "mju.ac.kr");
	}

	@Test
	void 가입한_사람은_액세스_토큰을_받는다() {
		when(verifier.verify("id-token")).thenReturn(workspaceIdentity());
		when(accounts.findSchoolByEmailDomain("mju.ac.kr")).thenReturn(Optional.of(school(1L)));
		when(accounts.recordLogin("sub-1")).thenReturn(Optional.of(new LoginUser(42L, "제자왕", UserRole.STUDENT)));

		GoogleLoginResponse response = service.login("id-token", IP);

		assertThat(response.registered()).isTrue();
		assertThat(response.user()).isEqualTo(new LoginUser(42L, "제자왕", UserRole.STUDENT));
		assertThat(jwtProvider.parseAccessToken(response.accessToken())).contains(new AuthUser(42L, UserRole.STUDENT));
		assertThat(response.signupToken()).isNull();
		assertThat(response.profile()).isNull();
	}

	@Test
	void 처음_온_사람은_가입_토큰과_가린_이메일을_받는다() {
		when(verifier.verify("id-token")).thenReturn(workspaceIdentity());
		when(accounts.findSchoolByEmailDomain("mju.ac.kr")).thenReturn(Optional.of(school(1L)));
		when(accounts.recordLogin("sub-1")).thenReturn(Optional.empty());

		GoogleLoginResponse response = service.login("id-token", IP);

		assertThat(response.registered()).isFalse();
		assertThat(jwtProvider.parseSignupToken(response.signupToken()))
			.contains(new SignupClaims("sub-1", "abc@mju.ac.kr", 1L));
		assertThat(response.profile().emailMasked()).isEqualTo("a***@mju.ac.kr");
		assertThat(response.accessToken()).isNull();
		assertThat(response.user()).isNull();
	}

	@Test
	void 가입_전에는_토큰_발급_외에_아무것도_저장하지_않는다() {
		when(verifier.verify("id-token")).thenReturn(workspaceIdentity());
		when(accounts.findSchoolByEmailDomain("mju.ac.kr")).thenReturn(Optional.of(school(1L)));
		when(accounts.recordLogin("sub-1")).thenReturn(Optional.empty());

		service.login("id-token", IP);

		verify(accounts).recordLogin("sub-1");   // 읽기 전용 조회. 사용자 생성은 가입(A-03)에서만 한다
	}

	@Test
	void 학교_도메인이_아니면_거부한다() {
		when(verifier.verify("id-token")).thenReturn(new GoogleIdentity("sub-2", "x@gmail.com", true, null));
		when(accounts.findSchoolByEmailDomain(null)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login("id-token", IP))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED));
		verify(accounts, never()).recordLogin(anyString());
	}

	@Test
	void 등록되지_않은_도메인이면_거부한다() {
		when(verifier.verify("id-token")).thenReturn(new GoogleIdentity("sub-3", "x@other.ac.kr", true, "other.ac.kr"));
		when(accounts.findSchoolByEmailDomain("other.ac.kr")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login("id-token", IP))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_DOMAIN_NOT_ALLOWED));
	}

	@Test
	void 이메일_인증이_안_된_계정은_거부한다() {
		when(verifier.verify("id-token")).thenReturn(new GoogleIdentity("sub-4", "abc@mju.ac.kr", false, "mju.ac.kr"));

		assertThatThrownBy(() -> service.login("id-token", IP))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID));
		verifyNoInteractions(accounts);
	}

	@Test
	void Google_토큰_검증이_실패하면_DB를_건드리지_않는다() {
		when(verifier.verify("forged")).thenThrow(new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID));

		assertThatThrownBy(() -> service.login("forged", IP))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID));
		verifyNoInteractions(accounts);
	}

	@Test
	void 탈퇴한_계정의_거부는_그대로_올려_보낸다() {
		when(verifier.verify("id-token")).thenReturn(workspaceIdentity());
		when(accounts.findSchoolByEmailDomain("mju.ac.kr")).thenReturn(Optional.of(school(1L)));
		when(accounts.recordLogin("sub-1")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

		assertThatThrownBy(() -> service.login("id-token", IP))
			.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
	}

	@Test
	void 같은_IP는_분당_10회까지만_부를_수_있고_초과하면_Google도_부르지_않는다() {
		when(verifier.verify("id-token")).thenReturn(workspaceIdentity());
		when(accounts.findSchoolByEmailDomain("mju.ac.kr")).thenReturn(Optional.of(school(1L)));
		when(accounts.recordLogin("sub-1")).thenReturn(Optional.empty());

		for (int i = 0; i < 10; i++) {
			service.login("id-token", IP);
		}

		assertThatThrownBy(() -> service.login("id-token", IP))
			.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
		verify(verifier, org.mockito.Mockito.times(10)).verify("id-token");

		// 다른 IP 는 따로 센다
		assertThat(service.login("id-token", "198.51.100.9").registered()).isFalse();
	}

}
