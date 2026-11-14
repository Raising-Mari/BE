package com.aidisciple.domain.teaching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.aidisciple.domain.teaching.dto.SendMessageRequest;
import com.aidisciple.domain.teaching.dto.TeachingTurnResponse;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

/**
 * T-01 가르치기 한 턴 (api-spec 11-1). 일부러 @Transactional 을 붙이지 않는다 —
 * AI 호출이 DB 트랜잭션 안에 들어가면 커넥션을 오래 잡으므로 읽기(prepare)와 저장(save)만 각각 짧은 트랜잭션으로 한다.
 * AI 가 실패하면 아무것도 저장되지 않아 FE 가 같은 clientMessageId 로 재시도할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class TeachingService {

	private static final String RATE_LIMIT_POLICY = "teaching.message";

	private final TeachingTurnStore turnStore;
	private final TeachingTurnEngine turnEngine;
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;

	public TeachingTurnResponse send(long userId, long discipleId, SendMessageRequest request) {
		rateLimiter.check(RATE_LIMIT_POLICY, rateLimitProperties.teachingMessage(), Long.toString(userId));

		TurnPreparation preparation = turnStore.prepare(userId, discipleId, request);
		if (preparation.isReplay()) {
			return preparation.replay();
		}
		TurnOutcome outcome = turnEngine.run(preparation.input());
		return turnStore.save(preparation, request, outcome);
	}

}
