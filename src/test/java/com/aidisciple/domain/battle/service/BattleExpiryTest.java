package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleRepository;

class BattleExpiryTest {

	private static final Instant NOW = Instant.parse("2026-11-09T01:00:00Z");

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final BattleExpiryStore store = new BattleExpiryStore(battleRepository, clock);

	private Battle room(long id, BattleStatus status, Instant expiresAt) {
		Battle battle = Battle.openRoom(1L, 2L, 5, 10L, "K7M4QX", expiresAt);
		ReflectionTestUtils.setField(battle, "id", id);
		ReflectionTestUtils.setField(battle, "status", status);
		when(battleRepository.findLockedById(id)).thenReturn(Optional.of(battle));
		return battle;
	}

	@Test
	void 입장_시간이_지난_대기실과_로비는_만료되고_코드가_풀린다() {
		Battle waiting = room(1L, BattleStatus.WAITING, NOW.minusSeconds(1));
		Battle lobby = room(2L, BattleStatus.LOBBY, NOW.minus(Duration.ofMinutes(5)));

		assertThat(store.expire(1L)).isTrue();
		assertThat(store.expire(2L)).isTrue();

		assertThat(waiting.getStatus()).isEqualTo(BattleStatus.EXPIRED);
		assertThat(lobby.getStatus()).isEqualTo(BattleStatus.EXPIRED);
		assertThat(waiting.getActiveRoomCode()).isNull();
		assertThat(waiting.getRoomCode()).isEqualTo("K7M4QX");   // 만료 안내를 위해 남는다
	}

	@Test
	void 찾은_뒤_그_사이에_시작했거나_취소된_방은_건드리지_않는다() {
		Battle started = room(3L, BattleStatus.RUNNING, NOW.minusSeconds(1));
		Battle canceled = room(4L, BattleStatus.CANCELED, NOW.minusSeconds(1));

		assertThat(store.expire(3L)).isFalse();
		assertThat(store.expire(4L)).isFalse();

		assertThat(started.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(canceled.getStatus()).isEqualTo(BattleStatus.CANCELED);
	}

	@Test
	void 아직_시간이_남은_방과_없는_방은_만료시키지_않는다() {
		Battle fresh = room(5L, BattleStatus.WAITING, NOW.plusSeconds(60));
		when(battleRepository.findLockedById(404L)).thenReturn(Optional.empty());

		assertThat(store.expire(5L)).isFalse();
		assertThat(store.expire(404L)).isFalse();
		assertThat(fresh.getStatus()).isEqualTo(BattleStatus.WAITING);
	}

	@Test
	void 작업은_지난_방을_하나씩_처리하고_하나가_실패해도_나머지를_계속한다() {
		BattleExpiryStore failing = mock(BattleExpiryStore.class);
		when(battleRepository.findIdsExpiredBefore(BattleStatus.OPEN_ROOM, NOW)).thenReturn(List.of(1L, 2L, 3L, 4L));
		when(failing.expire(1L)).thenReturn(true);
		when(failing.expire(2L)).thenThrow(new IllegalStateException("db"));
		when(failing.expire(3L)).thenReturn(false);
		when(failing.expire(4L)).thenReturn(true);
		BattleExpiryJob job = new BattleExpiryJob(battleRepository, failing, clock);

		int expired = job.sweep(NOW);

		assertThat(expired).isEqualTo(2);
		verify(failing).expire(4L);
	}

	@Test
	void 지난_방이_없으면_아무것도_하지_않는다() {
		BattleExpiryStore unused = mock(BattleExpiryStore.class);
		when(battleRepository.findIdsExpiredBefore(BattleStatus.OPEN_ROOM, NOW)).thenReturn(List.of());

		assertThat(new BattleExpiryJob(battleRepository, unused, clock).sweep(NOW)).isZero();
		org.mockito.Mockito.verify(unused, org.mockito.Mockito.never()).expire(anyLong());
	}

}
