package com.aidisciple.domain.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import com.aidisciple.domain.auth.config.AuthProperties;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.UserRole;

/**
 * 서버가 발급하는 액세스 JWT. 리프레시 토큰은 없고 만료되면 Google 로그인을 다시 한다.
 * 토큰마다 {@code purpose} 를 넣어, 나중에 추가할 가입 토큰 같은 다른 용도의 토큰이 액세스 토큰으로 통하지 못하게 한다.
 */
@Component
public class JwtProvider {

	private static final int MIN_SECRET_BYTES = 32;
	private static final String PURPOSE_CLAIM = "purpose";
	private static final String ROLE_CLAIM = "role";
	private static final String ACCESS_PURPOSE = "access";
	private static final String SIGNUP_PURPOSE = "signup";
	private static final String EMAIL_CLAIM = "email";
	private static final String SCHOOL_CLAIM = "schoolId";

	/** 가입 토큰은 Google 로그인 직후 가입 화면을 채우는 짧은 시간만 쓴다. */
	private static final Duration SIGNUP_EXPIRES_IN = Duration.ofMinutes(10);

	private final SecretKey key;
	private final Duration expiresIn;
	private final Clock clock;
	private final JwtParser parser;

	public JwtProvider(AuthProperties properties, Clock clock) {
		byte[] secret = properties.jwtSecret() == null ? new byte[0] : properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.auth.jwt-secret 은 32바이트 이상이어야 합니다. .env 의 JWT_SECRET 을 확인하세요.");
		}
		this.key = Keys.hmacShaKeyFor(secret);
		this.expiresIn = Duration.ofDays(properties.jwtExpiresDays());
		this.clock = clock;
		this.parser = Jwts.parser()
			.verifyWith(key)
			.clock(() -> Date.from(clock.instant()))
			.build();
	}

	public String issueAccessToken(long userId, UserRole role) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(Long.toString(userId))
			.claim(PURPOSE_CLAIM, ACCESS_PURPOSE)
			.claim(ROLE_CLAIM, role.name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(expiresIn)))
			.signWith(key)
			.compact();
	}

	/** 서명·만료·용도가 모두 맞는 토큰만 사용자로 돌려준다. 실패 이유는 밖으로 알리지 않는다. */
	public Optional<AuthUser> parseAccessToken(String token) {
		return parse(token, ACCESS_PURPOSE).flatMap(claims -> {
			try {
				long userId = Long.parseLong(claims.getSubject());
				UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
				return Optional.of(new AuthUser(userId, role));
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		});
	}

	/**
	 * 가입 토큰. Google 이 확인해 준 계정 정보를 10분 동안 가입 요청까지 실어 나른다.
	 * 한 번만 쓸 수 있다는 규칙은 토큰이 아니라 {@code users.google_sub} UNIQUE 와 가입 서비스의 중복 검사가 지킨다.
	 */
	public String issueSignupToken(String googleSub, String email, long schoolId) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(googleSub)
			.claim(PURPOSE_CLAIM, SIGNUP_PURPOSE)
			.claim(EMAIL_CLAIM, email)
			.claim(SCHOOL_CLAIM, schoolId)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(SIGNUP_EXPIRES_IN)))
			.signWith(key)
			.compact();
	}

	public Optional<SignupClaims> parseSignupToken(String token) {
		return parse(token, SIGNUP_PURPOSE).flatMap(claims -> {
			Object school = claims.get(SCHOOL_CLAIM);
			String email = claims.get(EMAIL_CLAIM, String.class);
			if (claims.getSubject() == null || email == null || !(school instanceof Number schoolId)) {
				return Optional.empty();
			}
			return Optional.of(new SignupClaims(claims.getSubject(), email, schoolId.longValue()));
		});
	}

	private Optional<Claims> parse(String token, String purpose) {
		try {
			Claims claims = parser.parseSignedClaims(token).getPayload();
			return purpose.equals(claims.get(PURPOSE_CLAIM, String.class)) ? Optional.of(claims) : Optional.empty();
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
