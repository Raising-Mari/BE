package com.aidisciple.domain.ranking.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.battle.entity.BattleResult;

class EloCalculatorTest {

	@Test
	void 레이팅이_같으면_기대_승률은_0점5다() {
		assertThat(EloCalculator.expectedScore(1000, 1000)).isEqualTo(0.5);
	}

	@Test
	void 두_사람의_기대_승률을_더하면_1이다() {
		assertThat(EloCalculator.expectedScore(1200, 1000) + EloCalculator.expectedScore(1000, 1200)).isCloseTo(1.0, within(1e-12));
		assertThat(EloCalculator.expectedScore(1500, 900) + EloCalculator.expectedScore(900, 1500)).isCloseTo(1.0, within(1e-12));
	}

	@Test
	void 레이팅이_400_높으면_기대_승률은_약_91퍼센트다() {
		assertThat(EloCalculator.expectedScore(1400, 1000)).isCloseTo(10.0 / 11.0, within(1e-12));
	}

	@Test
	void 같은_레이팅의_승패는_초기값_1000에서_16점이_오간다() {
		assertThat(EloCalculator.change(1000, 1000, BattleResult.WIN)).isEqualTo(new EloCalculator.Change(1000, 1016));
		assertThat(EloCalculator.change(1000, 1000, BattleResult.LOSE)).isEqualTo(new EloCalculator.Change(1000, 984));
	}

	@Test
	void 같은_레이팅의_무승부는_변하지_않는다() {
		EloCalculator.Change change = EloCalculator.change(1000, 1000, BattleResult.DRAW);

		assertThat(change.after()).isEqualTo(1000);
		assertThat(change.delta()).isZero();
	}

	@Test
	void 높은_쪽이_이기면_조금_오르고_낮은_쪽은_조금_내린다() {
		assertThat(EloCalculator.change(1200, 1000, BattleResult.WIN)).isEqualTo(new EloCalculator.Change(1200, 1208));   // 7.69
		assertThat(EloCalculator.change(1000, 1200, BattleResult.LOSE)).isEqualTo(new EloCalculator.Change(1000, 992));   // -7.69 → 992.31
	}

	@Test
	void 낮은_쪽이_이기면_많이_오르고_높은_쪽은_많이_내린다() {
		assertThat(EloCalculator.change(1000, 1200, BattleResult.WIN)).isEqualTo(new EloCalculator.Change(1000, 1024));   // 24.31
		assertThat(EloCalculator.change(1200, 1000, BattleResult.LOSE)).isEqualTo(new EloCalculator.Change(1200, 1176));   // -24.31
	}

	@Test
	void 무승부는_낮은_쪽이_오르고_높은_쪽이_내린다() {
		assertThat(EloCalculator.change(1200, 1000, BattleResult.DRAW)).isEqualTo(new EloCalculator.Change(1200, 1192));   // -8.31
		assertThat(EloCalculator.change(1000, 1200, BattleResult.DRAW)).isEqualTo(new EloCalculator.Change(1000, 1008));   // +8.31
	}

	@Test
	void 한_판의_변화는_K를_넘지_않는다() {
		for (int gap : new int[] {0, 100, 400, 1000}) {
			for (BattleResult result : BattleResult.values()) {
				assertThat(Math.abs(EloCalculator.change(1000 + gap, 1000, result).delta())).isLessThanOrEqualTo(EloCalculator.K);
				assertThat(Math.abs(EloCalculator.change(1000, 1000 + gap, result).delta())).isLessThanOrEqualTo(EloCalculator.K);
			}
		}
	}

	@Test
	void 두_사람의_변화를_더하면_반올림_오차_안에서_0이다() {
		for (int gap : new int[] {0, 37, 100, 250, 800}) {
			int winnerDelta = EloCalculator.change(1000 + gap, 1000, BattleResult.WIN).delta();
			int loserDelta = EloCalculator.change(1000, 1000 + gap, BattleResult.LOSE).delta();

			assertThat(Math.abs(winnerDelta + loserDelta)).isLessThanOrEqualTo(1);
		}
	}

	@Test
	void 압도적으로_높은_사람이_이기면_거의_변하지_않는다() {
		assertThat(EloCalculator.change(2000, 1000, BattleResult.WIN).delta()).isZero();   // 기대 승률 99.7%
		assertThat(EloCalculator.change(1000, 2000, BattleResult.WIN).delta()).isEqualTo(32 - 0);   // 이변: 거의 K 만큼
	}

	@Test
	void 승패_점수는_승_1_무_0점5_패_0이다() {
		assertThat(EloCalculator.scoreOf(BattleResult.WIN)).isEqualTo(1.0);
		assertThat(EloCalculator.scoreOf(BattleResult.DRAW)).isEqualTo(0.5);
		assertThat(EloCalculator.scoreOf(BattleResult.LOSE)).isEqualTo(0.0);
	}

	@Test
	void 초기값과_K는_계획서의_가정과_같다() {
		assertThat(EloCalculator.INITIAL_RATING).isEqualTo(1000);
		assertThat(EloCalculator.K).isEqualTo(32);
	}

}
