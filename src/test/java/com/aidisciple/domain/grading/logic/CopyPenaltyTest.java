package com.aidisciple.domain.grading.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aidisciple.domain.grading.config.ScoringProperties;

class CopyPenaltyTest {

	private static final String MATERIAL = "스택은 가장 나중에 들어간 자료가 가장 먼저 나오는 후입선출 구조이다.";

	private final CopyPenalty penalty = new CopyPenalty(ScoringProperties.defaults());

	@Test
	void 비율이_0점5_이하면_계수는_1이다() {
		assertThat(penalty.factor(0.0)).isEqualByComparingTo("1.00");
		assertThat(penalty.factor(0.5)).isEqualByComparingTo("1.00");
	}

	@Test
	void 비율이_0점9_이상이면_계수는_최소값_0점5다() {
		assertThat(penalty.factor(0.9)).isEqualByComparingTo("0.50");
		assertThat(penalty.factor(1.0)).isEqualByComparingTo("0.50");
	}

	@Test
	void 사이는_선형이고_소수_둘째_자리로_반올림한다() {
		assertThat(penalty.factor(0.7)).isEqualByComparingTo("0.75");
		assertThat(penalty.factor(0.6)).isEqualByComparingTo("0.88");   // 0.875
		assertThat(penalty.factor(0.51)).isEqualByComparingTo("0.99");   // 0.9875
		assertThat(penalty.factor(0.89)).isEqualByComparingTo("0.51");   // 0.5125
	}

	@Test
	void 비율이_커질수록_계수는_작아진다() {
		assertThat(penalty.factor(0.55)).isGreaterThan(penalty.factor(0.65));
		assertThat(penalty.factor(0.65)).isGreaterThan(penalty.factor(0.85));
	}

	@Test
	void 자료를_그대로_베낀_답은_비율이_1이다() {
		Set<String> grams = penalty.materialGrams(List.of(MATERIAL));

		CopyPenalty.Ratios ratios = penalty.ratios(grams, MATERIAL, List.of());

		assertThat(ratios.answer()).isEqualTo(1.0);
		assertThat(ratios.combined()).isEqualTo(1.0);
	}

	@Test
	void 자기_말로_바꿔_쓴_답은_비율이_낮다() {
		Set<String> grams = penalty.materialGrams(List.of(MATERIAL));

		CopyPenalty.Ratios ratios = penalty.ratios(grams, "마지막에 넣은 걸 제일 먼저 꺼내는 방식이야", List.of());

		assertThat(ratios.answer()).isLessThan(0.2);
	}

	@Test
	void 짧은_정답은_자료에_그대로_있어도_복붙이_아니다() {
		Set<String> grams = penalty.materialGrams(List.of(MATERIAL));

		CopyPenalty.Ratios ratios = penalty.ratios(grams, "후입선출", List.of());   // 8자 미만이라 n-gram 이 없다

		assertThat(ratios.answer()).isZero();
		assertThat(penalty.factor(ratios.combined())).isEqualByComparingTo("1.00");
	}

	@Test
	void 인용한_노트가_자료를_베꼈으면_답이_짧아도_노트_비율로_보정된다() {
		Set<String> grams = penalty.materialGrams(List.of(MATERIAL));

		CopyPenalty.Ratios ratios = penalty.ratios(grams, "후입선출", List.of(MATERIAL));

		assertThat(ratios.answer()).isZero();
		assertThat(ratios.notes()).isEqualTo(1.0);
		assertThat(ratios.combined()).isEqualTo(1.0);
		assertThat(penalty.factor(ratios.combined())).isEqualByComparingTo("0.50");
	}

	@Test
	void 자료가_없거나_빈_답이면_비율은_0이다() {
		assertThat(penalty.ratios(Set.of(), MATERIAL, List.of(MATERIAL)).combined()).isZero();
		assertThat(penalty.ratios(penalty.materialGrams(List.of(MATERIAL)), "", List.of()).combined()).isZero();
		assertThat(penalty.ratios(penalty.materialGrams(List.of(MATERIAL)), null, List.of()).combined()).isZero();
	}

	@Test
	void 자료_n_gram은_문장_경계를_넘지_않는다() {
		Set<String> two = penalty.materialGrams(List.of("가나다라마바사아", "자차카타파하거너"));
		Set<String> merged = penalty.materialGrams(List.of("가나다라마바사아자차카타파하거너"));

		assertThat(two).hasSize(2);
		assertThat(merged).hasSize(9);
	}

}
