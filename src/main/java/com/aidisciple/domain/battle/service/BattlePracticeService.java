package com.aidisciple.domain.battle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.aidisciple.domain.battle.dto.CreatePracticeRequest;
import com.aidisciple.domain.battle.dto.CreatePracticeResponse;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

/**
 * B-10 의 바깥 문. <b>트랜잭션을 걸지 않는다</b>: {@link BattlePracticeStore} 가 커밋한 뒤에 풀이를 접수해
 * 곧바로 시작되는 풀이 스레드가 커밋 전 상태를 못 보는 일이 없게 한다.
 * 사용량 제한은 방 만들기(B-01)와 같은 이름으로 함께 센다(10분 5회).
 */
@Service
@RequiredArgsConstructor
public class BattlePracticeService {

	private final BattlePracticeStore store;
	private final BattleSolveDispatcher dispatcher;
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;

	public CreatePracticeResponse start(long userId, CreatePracticeRequest request) {
		rateLimiter.check(BattleRoomService.RATE_LIMIT_POLICY, rateLimitProperties.battleCreate(), String.valueOf(userId));
		BattlePracticeStore.Started started = store.create(userId, request);
		dispatcher.submit(started.participantId());
		return started.response();
	}

}
