package com.aidisciple.domain.grading.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.grading.config.ScoringProperties;
import com.aidisciple.domain.question.entity.GradingMode;

/**
 * 판정을 점수로 바꾼다(api-spec 11-4 의 5·6). 정답 +1, 부분 정답 +0.5, 모름·주입·판정 불가 0, 오답은 채점 모드별 감점(λ).
 * 찍어서 맞히는 쪽이 유리해지지 않도록 오답은 감점이고, 복붙 보정 계수는 <b>정답·부분 정답에만</b> 곱한다(감점에는 곱하지 않는다).
 */
public final class ScoreCalculator {

	private static final int SCALE = 2;

	private final ScoringProperties properties;

	public ScoreCalculator(ScoringProperties properties) {
		this.properties = properties;
	}

	/** 복붙 보정 전의 점수 */
	public BigDecimal rawScore(Verdict verdict, GradingMode mode) {
		BigDecimal score = switch (verdict) {
			case CORRECT -> properties.correct();
			case PARTIAL -> properties.partial();
			case WRONG -> penalty(mode).negate();
			case UNKNOWN, ZEROED_INJECTION, UNJUDGED, PENDING -> BigDecimal.ZERO;
		};
		return score.setScale(SCALE, RoundingMode.HALF_UP);
	}

	/** 복붙 보정을 반영한 최종 점수. 소수 둘째 자리에서 반올림한다. */
	public BigDecimal finalScore(Verdict verdict, BigDecimal rawScore, BigDecimal copyFactor) {
		if (verdict == Verdict.CORRECT || verdict == Verdict.PARTIAL) {
			return rawScore.multiply(copyFactor).setScale(SCALE, RoundingMode.HALF_UP);
		}
		return rawScore;
	}

	private BigDecimal penalty(GradingMode mode) {
		return mode == GradingMode.KEY_ELEMENTS ? properties.wrongKeyElements() : properties.wrongAnswerList();
	}

}
