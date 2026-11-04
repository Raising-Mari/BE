package com.aidisciple.global.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.rate-limit.*}. 기본값과 의미는 application.yml 에 있다. 실제 적용은 각 기능이 {@link RateLimiter} 로 한다. */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Rule googleLogin, Rule teachingMessage, Rule battleCreate, Rule materialUpload) {

	/** {@code window} 안에 {@code limit} 회까지 허용한다. */
	public record Rule(int limit, Duration window) {
	}

}
