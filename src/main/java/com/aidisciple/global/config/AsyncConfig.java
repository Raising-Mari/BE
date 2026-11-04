package com.aidisciple.global.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.aidisciple.global.ratelimit.RateLimitProperties;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class AsyncConfig {

	/**
	 * {@code @Async} 의 기본 실행기. 크기를 제한해 문항 생성·풀이 같은 작업이 서버를 잠식하지 못하게 한다.
	 * 큐가 차면 조용히 버리지 않고 예외가 나게 둬서(AbortPolicy) 작업 상태를 DB 에서 실패로 남길 수 있게 한다.
	 */
	@Bean(name = "taskExecutor")
	ThreadPoolTaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("bg-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		return executor;
	}

}
