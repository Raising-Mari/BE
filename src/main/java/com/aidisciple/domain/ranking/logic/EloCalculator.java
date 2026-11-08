package com.aidisciple.domain.ranking.logic;

import com.aidisciple.domain.battle.entity.BattleResult;

/**
 * Elo 계산(api-spec 11-5). DB·AI 를 모르는 순수 클래스다.
 * 기대 승률 {@code E = 1 / (1 + 10^((상대 − 나) / 400))}, 갱신 {@code R' = round(R + K·(S − E))}, K = 32, 승 1·무 0.5·패 0, 초기 1000.
 * K 와 초기값은 계획서의 초기 가정이다 — 바꾸면 문서도 같이 고친다.
 */
public final class EloCalculator {

	public static final int INITIAL_RATING = 1000;
	public static final int K = 32;

	private EloCalculator() {
	}

	/** 한 사람의 레이팅 변화 */
	public record Change(int before, int after) {

		public int delta() {
			return after - before;
		}

	}

	/** 내가 이길 것으로 기대되는 정도(0~1). 레이팅이 같으면 0.5 다. */
	public static double expectedScore(int own, int opponent) {
		return 1.0 / (1.0 + Math.pow(10.0, (opponent - own) / 400.0));
	}

	public static double scoreOf(BattleResult result) {
		return switch (result) {
			case WIN -> 1.0;
			case DRAW -> 0.5;
			case LOSE -> 0.0;
		};
	}

	/** 반올림은 {@link Math#round(double)}(0.5 는 올림)다. */
	public static Change change(int own, int opponent, BattleResult result) {
		double expected = expectedScore(own, opponent);
		int after = (int) Math.round(own + K * (scoreOf(result) - expected));
		return new Change(own, after);
	}

}
