package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.battle.dto.CreatePracticeRequest;
import com.aidisciple.domain.battle.dto.CreatePracticeResponse;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

class BattlePracticeServiceTest {

	private final BattlePracticeStore store = mock(BattlePracticeStore.class);
	private final BattleSolveDispatcher dispatcher = mock(BattleSolveDispatcher.class);
	private final RateLimiter rateLimiter = new RateLimiter(Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC));
	private final RateLimitProperties rateLimitProperties = new RateLimitProperties(
		new RateLimitProperties.Rule(10, Duration.ofMinutes(1)), new RateLimitProperties.Rule(12, Duration.ofMinutes(1)),
		new RateLimitProperties.Rule(5, Duration.ofMinutes(10)), new RateLimitProperties.Rule(10, Duration.ofHours(1)));
	private final BattlePracticeService service = new BattlePracticeService(store, dispatcher, rateLimiter, rateLimitProperties);

	private final CreatePracticeRequest request = new CreatePracticeRequest(50L, null);
	private final CreatePracticeResponse response = new CreatePracticeResponse(60L, BattleMode.PRACTICE, BattleStatus.RUNNING, 3, List.of());

	@Test
	void 만든_뒤에_참가자의_풀이를_접수한다() {
		when(store.create(10L, request)).thenReturn(new BattlePracticeStore.Started(response, 6L));

		CreatePracticeResponse result = service.start(10L, request);

		assertThat(result).isSameAs(response);
		verify(dispatcher).submit(6L);
	}

	@Test
	void 만들기가_거부되면_풀이를_접수하지_않는다() {
		when(store.create(10L, request)).thenThrow(new BusinessException(ErrorCode.PRACTICE_NOTHING_TO_REVIEW));

		Throwable thrown = catchThrowable(() -> service.start(10L, request));

		assertThat(thrown).isInstanceOf(BusinessException.class);
		verify(dispatcher, never()).submit(anyLong());
	}

	@Test
	void 사용량_제한은_방_만들기와_같은_이름으로_함께_센다() {
		when(store.create(anyLong(), any())).thenReturn(new BattlePracticeStore.Started(response, 6L));
		// 방 만들기(B-01)가 같은 이름으로 4번 센 상태를 흉내 낸다
		for (int i = 0; i < 4; i++) {
			rateLimiter.check(BattleRoomService.RATE_LIMIT_POLICY, rateLimitProperties.battleCreate(), "10");
		}

		service.start(10L, request);   // 5번째
		Throwable thrown = catchThrowable(() -> service.start(10L, request));   // 6번째

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
	}

}
