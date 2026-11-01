package com.aidisciple.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.auth.*}. 비밀키와 클라이언트 ID 는 {@code .env} 에서만 받는다. */
@ConfigurationProperties("app.auth")
public record AuthProperties(
	String googleClientId,
	String jwtSecret,
	@DefaultValue("7") int jwtExpiresDays
) {
}
