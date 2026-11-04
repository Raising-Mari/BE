package com.aidisciple.global.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 키(사용자 id, IP 등)별 고정 윈도우 카운터. 서버 한 대 메모리에서만 세므로 재시작하면 초기화된다.
 * 윈도우는 그 키의 첫 요청에서 시작해 {@code window} 뒤에 끝난다.
 */
@Component
public class RateLimiter {

	private static final long MAX_KEYS_PER_POLICY = 100_000;

	private final Ticker ticker;
	private final ConcurrentMap<String, Cache<String, AtomicInteger>> caches = new ConcurrentHashMap<>();

	public RateLimiter(Clock clock) {
		// 시간 원천을 Clock 하나로 맞춰 테스트에서 시각을 고정할 수 있게 한다.
		this.ticker = () -> Duration.between(Instant.EPOCH, clock.instant()).toNanos();
	}

	/**
	 * 한도를 넘으면 {@code RATE_LIMITED} 를 던진다.
	 *
	 * @param policy 한도를 구분하는 이름. 같은 이름끼리만 카운터를 공유한다 (예: {@code "auth.google-login"})
	 * @param key    누구의 요청인지 (예: IP, 사용자 id)
	 */
	public void check(String policy, RateLimitProperties.Rule rule, String key) {
		Cache<String, AtomicInteger> cache = caches.computeIfAbsent(policy, name -> Caffeine.newBuilder()
			.ticker(ticker)
			.expireAfterWrite(rule.window())
			.maximumSize(MAX_KEYS_PER_POLICY)
			.build());
		if (cache.get(key, k -> new AtomicInteger()).incrementAndGet() > rule.limit()) {
			throw new BusinessException(ErrorCode.RATE_LIMITED);
		}
	}

}
