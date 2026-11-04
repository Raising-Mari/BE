package com.aidisciple.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	/** 만료·시각 판단은 {@code Instant.now()} 대신 이 빈을 주입받아 쓴다. 테스트에서 시각을 고정할 수 있다. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
