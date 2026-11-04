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
		try {
			Claims claims = parser.parseSignedClaims(token).getPayload();
			if (!ACCESS_PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
				return Optional.empty();
			}
			long userId = Long.parseLong(claims.getSubject());
			UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
			return Optional.of(new AuthUser(userId, role));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
