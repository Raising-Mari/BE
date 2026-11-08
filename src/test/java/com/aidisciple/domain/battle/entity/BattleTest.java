package com.aidisciple.domain.battle.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class BattleTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

	private static Battle waiting() {
		return Battle.openRoom(1L, 2L, 5, 10L, "K7M4QX", NOW.plusSeconds(1800));
	}

	private static Battle lobby() {
		Battle battle = waiting();
		battle.markLobby();
		return battle;
	}

	private static Battle running() {
		Battle battle = lobby();
		battle.start(NOW);
		return battle;
	}

	private static void assertConflict(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
	}

	@Test
	void 방을_만들면_WAITING이고_진행_중_코드가_있다() {
		Battle battle = waiting();

		assertThat(battle.getStatus()).isEqualTo(BattleStatus.WAITING);
		assertThat(battle.getMode()).isEqualTo(BattleMode.RANKED);
		assertThat(battle.getRoomCode()).isEqualTo("K7M4QX");
		assertThat(battle.getActiveRoomCode()).isEqualTo("K7M4QX");
	}

	@Test
	void 정상_흐름은_WAITING_LOBBY_RUNNING_FINISHED이고_시작하면_진행_중_코드가_비워진다() {
		Battle battle = lobby();
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.LOBBY);
		assertThat(battle.getActiveRoomCode()).isEqualTo("K7M4QX");

		battle.start(NOW);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(battle.getStartedAt()).isEqualTo(NOW);
		assertThat(battle.getActiveRoomCode()).isNull();
		assertThat(battle.getRoomCode()).isEqualTo("K7M4QX");   // 만료·취소 안내용으로 남는다

		battle.finish(NOW.plusSeconds(60));
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
		assertThat(battle.getFinishedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void 대기실에서는_취소와_만료가_되고_코드가_비워진다() {
		Battle canceled = waiting();
		canceled.cancel();
		assertThat(canceled.getStatus()).isEqualTo(BattleStatus.CANCELED);
		assertThat(canceled.getActiveRoomCode()).isNull();

		Battle expired = lobby();
		expired.expire();
		assertThat(expired.getStatus()).isEqualTo(BattleStatus.EXPIRED);
		assertThat(expired.getActiveRoomCode()).isNull();
	}

	@Test
	void 풀이가_실패하면_ERROR가_되고_재시도하면_RUNNING으로_돌아온다() {
		Battle battle = running();

		battle.markError("AI_UNAVAILABLE");
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.ERROR);
		assertThat(battle.getErrorReason()).isEqualTo("AI_UNAVAILABLE");

		battle.retrySolve();
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(battle.getErrorReason()).isNull();
	}

	@Test
	void ERROR에서는_포기하고_취소할_수_있다() {
		Battle battle = running();
		battle.markError("AI_TIMEOUT");

		battle.cancel();

		assertThat(battle.getStatus()).isEqualTo(BattleStatus.CANCELED);
	}

	@Test
	void 풀이_중이나_끝난_배틀은_취소할_수_없다() {
		assertConflict(() -> running().cancel());

		Battle finished = running();
		finished.finish(NOW);
		assertConflict(finished::cancel);
	}

	@Test
	void 허용되지_않은_전이는_CONFLICT다() {
		assertConflict(() -> waiting().start(NOW));              // 상대가 들어오기 전에는 시작할 수 없다
		assertConflict(() -> lobby().finish(NOW));               // 풀이 전에는 끝낼 수 없다
		assertConflict(() -> lobby().markError("X"));
		assertConflict(() -> running().retrySolve());            // ERROR 가 아니면 재시도할 수 없다
		assertConflict(() -> running().expire());                // 시작한 방은 만료되지 않는다
		assertConflict(() -> running().markLobby());

		Battle canceled = waiting();
		canceled.cancel();
		assertConflict(canceled::markLobby);
		assertConflict(canceled::cancel);
		assertConflict(canceled::expire);
	}

	@Test
	void 입장_시간이_지난_대기실은_만료로_본다() {
		Battle battle = waiting();

		assertThat(battle.isExpiredAt(NOW.plusSeconds(1799))).isFalse();
		assertThat(battle.isExpiredAt(NOW.plusSeconds(1800))).isTrue();
		assertThat(running().isExpiredAt(NOW.plusSeconds(99999))).isFalse();   // 시작한 방은 시간으로 만료되지 않는다
	}

	@Test
	void 연습_배틀은_방_코드_없이_RUNNING으로_시작한다() {
		Battle practice = Battle.startPractice(1L, 2L, 9L, 2, 10L, NOW);

		assertThat(practice.getMode()).isEqualTo(BattleMode.PRACTICE);
		assertThat(practice.isPractice()).isTrue();
		assertThat(practice.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(practice.getSourceBattleId()).isEqualTo(9L);
		assertThat(practice.getRoomCode()).isNull();
		assertThat(practice.getExpiresAt()).isNull();
		assertThat(practice.isExpiredAt(NOW.plusSeconds(99999))).isFalse();
	}

	@Test
	void 오류_이유는_컬럼_길이로_자른다() {
		Battle battle = running();

		battle.markError("E".repeat(300));

		assertThat(battle.getErrorReason()).hasSize(100);
	}

}
