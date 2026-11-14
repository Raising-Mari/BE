package com.aidisciple.domain.course.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class UnitTitleOrderTest {

	private static List<String> sorted(String... titles) {
		List<String> list = new ArrayList<>(List.of(titles));
		list.sort(UnitTitleOrder::compare);
		return list;
	}

	@Test
	void 숫자는_값으로_비교한다() {
		assertThat(sorted("10주차", "2주차", "1주차", "11주차", "3주차"))
			.containsExactly("1주차", "2주차", "3주차", "10주차", "11주차");
	}

	@Test
	void 글자_앞뒤의_숫자도_값으로_비교한다() {
		assertThat(sorted("제10장", "제2장", "제1장")).containsExactly("제1장", "제2장", "제10장");
		assertThat(sorted("2주차 실습", "2주차 이론", "2주차")).containsExactly("2주차", "2주차 실습", "2주차 이론");
	}

	@Test
	void 앞에_0이_붙은_숫자는_같은_값이다() {
		assertThat(UnitTitleOrder.compare("01주차", "1주차")).isZero();
		assertThat(UnitTitleOrder.compare("02주차", "10주차")).isNegative();
	}

	@Test
	void 숫자가_없으면_글자_순서이고_대소문자는_구분하지_않는다() {
		assertThat(sorted("중간고사", "기말고사", "오리엔테이션")).containsExactly("기말고사", "오리엔테이션", "중간고사");
		assertThat(UnitTitleOrder.compare("Intro", "intro")).isZero();
		assertThat(sorted("b", "A", "c")).containsExactly("A", "b", "c");
	}

	@Test
	void 한쪽이_다른_쪽의_앞부분이면_짧은_쪽이_앞이다() {
		assertThat(UnitTitleOrder.compare("1주차", "1주차 보충")).isNegative();
		assertThat(UnitTitleOrder.compare("", "1주차")).isNegative();
	}

	@Test
	void 매우_큰_숫자도_넘치지_않고_비교한다() {
		assertThat(UnitTitleOrder.compare("99999999999999999999주차", "100000000000000000000주차")).isNegative();
	}

}
