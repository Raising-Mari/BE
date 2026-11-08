package com.aidisciple.domain.grading.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.grading.config.ScoringProperties;
import com.aidisciple.domain.question.entity.GradingMode;

class ScoreCalculatorTest {

	private final ScoreCalculator calculator = new ScoreCalculator(ScoringProperties.defaults());

	@Test
	void 정답은_1점_부분_정답은_0점5다() {
		assertThat(calculator.rawScore(Verdict.CORRECT, GradingMode.ANSWER_LIST)).isEqualByComparingTo("1");
		assertThat(calculator.rawScore(Verdict.PARTIAL, GradingMode.KEY_ELEMENTS)).isEqualByComparingTo("0.5");
	}

	@Test
	void 오답의_감점은_채점_모드별이다() {
		assertThat(calculator.rawScore(Verdict.WRONG, GradingMode.ANSWER_LIST)).isEqualByComparingTo("-0.5");
		assertThat(calculator.rawScore(Verdict.WRONG, GradingMode.KEY_ELEMENTS)).isEqualByComparingTo("-0.3");
	}

	@Test
	void 모름_지시문_판정_불가는_0점이고_감점이_없다() {
		for (Verdict verdict : new Verdict[] {Verdict.UNKNOWN, Verdict.ZEROED_INJECTION, Verdict.UNJUDGED}) {
			assertThat(calculator.rawScore(verdict, GradingMode.ANSWER_LIST)).isEqualByComparingTo("0");
			assertThat(calculator.rawScore(verdict, GradingMode.KEY_ELEMENTS)).isEqualByComparingTo("0");
		}
	}

	@Test
	void 점수는_소수_둘째_자리다() {
		assertThat(calculator.rawScore(Verdict.WRONG, GradingMode.KEY_ELEMENTS).scale()).isEqualTo(2);
		assertThat(calculator.rawScore(Verdict.CORRECT, GradingMode.ANSWER_LIST).scale()).isEqualTo(2);
	}

	@Test
	void 복붙_계수는_정답과_부분_정답에만_곱하고_소수_둘째_자리에서_반올림한다() {
		BigDecimal factor = new BigDecimal("0.75");

		assertThat(calculator.finalScore(Verdict.CORRECT, new BigDecimal("1.00"), factor)).isEqualByComparingTo("0.75");
		assertThat(calculator.finalScore(Verdict.PARTIAL, new BigDecimal("0.50"), factor)).isEqualByComparingTo("0.38");   // 0.375
	}

	@Test
	void 감점과_0점에는_복붙_계수를_곱하지_않는다() {
		BigDecimal factor = new BigDecimal("0.50");

		assertThat(calculator.finalScore(Verdict.WRONG, new BigDecimal("-0.50"), factor)).isEqualByComparingTo("-0.50");
		assertThat(calculator.finalScore(Verdict.UNKNOWN, BigDecimal.ZERO.setScale(2), factor)).isEqualByComparingTo("0");
	}

	@Test
	void 설정을_바꾸면_점수가_바뀐다() {
		ScoringProperties custom = new ScoringProperties(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("1"),
			new BigDecimal("0.4"), 0.5, 0.9, new BigDecimal("0.5"), 8);

		assertThat(new ScoreCalculator(custom).rawScore(Verdict.CORRECT, GradingMode.ANSWER_LIST)).isEqualByComparingTo("2");
		assertThat(new ScoreCalculator(custom).rawScore(Verdict.WRONG, GradingMode.KEY_ELEMENTS)).isEqualByComparingTo("-0.4");
	}

}
