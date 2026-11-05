package com.aidisciple.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import com.aidisciple.domain.auth.config.AuthProperties;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.UserRole;

class JwtProviderTest {

	private static final String SECRET = "unit-test-secret-key-0123456789-abcdefghij";
	private static final Instant START = Instant.parse("2026-11-06T00:00:00Z");

	/** 테스트에서 시각을 직접 옮길 수 있는 시계 */
	private static final class MutableClock extends Clock {
		private Instant now = START;

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	private final MutableClock clock = new MutableClock();
	private final JwtProvider provider = new JwtProvider(new AuthProperties("client", SECRET, 7), clock);

	// --- 액세스 토큰 ---

	@Test
	void 발급한_액세스_토큰에서_사용자와_역할을_읽는다() {
		String token = provider.issueAccessToken(42L, UserRole.STUDENT);

		assertThat(provider.parseAccessToken(token)).contains(new AuthUser(42L, UserRole.STUDENT));
		assertThat(provider.parseAccessToken(provider.issueAccessToken(1L, UserRole.ADMIN)))
			.contains(new AuthUser(1L, UserRole.ADMIN));
	}

	@Test
	void 액세스_토큰은_7일_뒤에_만료된다() {
		String token = provider.issueAccessToken(42L, UserRole.STUDENT);

		clock.advance(Duration.ofDays(7).minusSeconds(1));
		assertThat(provider.parseAccessToken(token)).isPresent();

		clock.advance(Duration.ofSeconds(2));
		assertThat(provider.parseAccessToken(token)).isEmpty();
	}

	@Test
	void 서명을_바꾸거나_다른_키로_만든_토큰은_거부한다() {
		String token = provider.issueAccessToken(42L, UserRole.STUDENT);
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
		JwtProvider otherKey = new JwtProvider(new AuthProperties("client", "another-secret-key-0123456789-abcdefghijk", 7), clock);

		assertThat(provider.parseAccessToken(tampered)).isEmpty();
		assertThat(provider.parseAccessToken(otherKey.issueAccessToken(42L, UserRole.ADMIN))).isEmpty();
	}

	@Test
	void 내용만_바꾼_토큰은_거부한다() {
		String[] parts = provider.issueAccessToken(42L, UserRole.STUDENT).split("\\.");
		String adminPayload = java.util.Base64.getUrlEncoder().withoutPadding()
			.encodeToString("{\"sub\":\"42\",\"purpose\":\"access\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

		assertThat(provider.parseAccessToken(parts[0] + "." + adminPayload + "." + parts[2])).isEmpty();
	}

	@Test
	void 서명이_없는_토큰은_거부한다() {
		String unsigned = Jwts.builder()
			.subject("1")
			.claim("purpose", "access")
			.claim("role", "ADMIN")
			.expiration(Date.from(START.plus(Duration.ofDays(1))))
			.compact();

		assertThat(provider.parseAccessToken(unsigned)).isEmpty();
	}

	@Test
	void 토큰이_아닌_값은_거부한다() {
		assertThat(provider.parseAccessToken(null)).isEmpty();
		assertThat(provider.parseAccessToken("")).isEmpty();
		assertThat(provider.parseAccessToken("not-a-jwt")).isEmpty();
		assertThat(provider.parseAccessToken("a.b.c")).isEmpty();
	}

	// --- 가입 토큰 ---

	@Test
	void 가입_토큰에서_Google_계정_정보와_학교를_읽는다() {
		String token = provider.issueSignupToken("google-sub-1", "abc@mju.ac.kr", 3L);

		assertThat(provider.parseSignupToken(token))
			.contains(new SignupClaims("google-sub-1", "abc@mju.ac.kr", 3L));
	}

	@Test
	void 가입_토큰은_10분_뒤에_만료된다() {
		String token = provider.issueSignupToken("google-sub-1", "abc@mju.ac.kr", 3L);

		clock.advance(Duration.ofMinutes(10).minusSeconds(1));
		assertThat(provider.parseSignupToken(token)).isPresent();

		clock.advance(Duration.ofSeconds(2));
		assertThat(provider.parseSignupToken(token)).isEmpty();
	}

	@Test
	void 가입_토큰은_액세스_토큰으로_통하지_않고_그_반대도_마찬가지다() {
		String signup = provider.issueSignupToken("42", "abc@mju.ac.kr", 3L);
		String access = provider.issueAccessToken(42L, UserRole.ADMIN);

		assertThat(provider.parseAccessToken(signup)).isEmpty();
		assertThat(provider.parseSignupToken(access)).isEmpty();
	}

	// --- 키 ---

	@Test
	void 비밀키가_32바이트_미만이면_시작하지_못한다() {
		assertThatThrownBy(() -> new JwtProvider(new AuthProperties("client", "too-short", 7), clock))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new JwtProvider(new AuthProperties("client", "", 7), clock))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new JwtProvider(new AuthProperties("client", null, 7), clock))
			.isInstanceOf(IllegalStateException.class);
	}

}
