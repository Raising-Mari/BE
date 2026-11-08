package com.aidisciple.domain.battle.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.battle.*}. 값의 의미는 application.yml 주석. 테스트에서는 {@link #defaults()} 로 만든다. */
@ConfigurationProperties("app.battle")
public record BattleProperties(
	@DefaultValue("30m") Duration roomTtl,
	@DefaultValue("3") int solveMaxAttempts,
	@DefaultValue("2s") Duration solveRetryBackoff,
	@DefaultValue("1m") Duration expirySweepInterval
) {

	public static BattleProperties defaults() {
		return new BattleProperties(Duration.ofMinutes(30), 3, Duration.ofSeconds(2), Duration.ofMinutes(1));
	}

}
