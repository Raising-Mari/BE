package com.aidisciple.domain.ranking.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.battle.entity.BattleResult;

class EloEntityTest {

	@Test
	void 처음에는_1000점이고_전적이_없다() {
		EloRating rating = EloRating.initial(10L, 1L);

		assertThat(rating.getRating()).isEqualTo(1000);
		assertThat(rating.getGamesPlayed()).isZero();
		assertThat(rating.getWins() + rating.getLosses() + rating.getDraws()).isZero();
		assertThat(rating.getUserId()).isEqualTo(10L);
		assertThat(rating.getCourseId()).isEqualTo(1L);
	}

	@Test
	void 결과를_반영하면_레이팅과_경기_수와_승무패가_바뀐다() {
		EloRating rating = EloRating.initial(10L, 1L);

		rating.applyResult(1016, BattleResult.WIN);
		rating.applyResult(1000, BattleResult.LOSE);
		rating.applyResult(1000, BattleResult.DRAW);
		rating.applyResult(1018, BattleResult.WIN);

		assertThat(rating.getRating()).isEqualTo(1018);
		assertThat(rating.getGamesPlayed()).isEqualTo(4);
		assertThat(rating.getWins()).isEqualTo(2);
		assertThat(rating.getLosses()).isEqualTo(1);
		assertThat(rating.getDraws()).isEqualTo(1);
	}

	@Test
	void 이력은_변화량을_계산해_담는다() {
		EloHistory up = EloHistory.of(10L, 1L, 50L, 1000, 1016);
		EloHistory down = EloHistory.of(11L, 1L, 50L, 1000, 984);

		assertThat(up.getDelta()).isEqualTo(16);
		assertThat(down.getDelta()).isEqualTo(-16);
		assertThat(up.getBattleId()).isEqualTo(50L);
		assertThat(up.getRatingBefore()).isEqualTo(1000);
		assertThat(up.getRatingAfter()).isEqualTo(1016);
	}

}
