package com.aidisciple.domain.course.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameNormalizerTest {

	@Test
	void 공백_유무와_대소문자가_달라도_같은_이름이다() {
		assertThat(NameNormalizer.normalize("3주차")).isEqualTo(NameNormalizer.normalize("3 주차"));
		assertThat(NameNormalizer.normalize("자료구조")).isEqualTo(NameNormalizer.normalize("  자료  구조 "));
		assertThat(NameNormalizer.normalize("Data Structure")).isEqualTo(NameNormalizer.normalize("data structure"));
		assertThat(NameNormalizer.normalize("DataStructure")).isEqualTo(NameNormalizer.normalize("data structure"));
	}

	@Test
	void 전각_문자와_보이지_않는_문자도_같은_이름으로_본다() {
		assertThat(NameNormalizer.normalize("３주차")).isEqualTo("3주차");          // 전각 숫자
		assertThat(NameNormalizer.normalize("자료" + (char) 0x200B + "구조")).isEqualTo("자료구조");   // 폭 없는 공백
		assertThat(NameNormalizer.normalize("자료" + (char) 0x00A0 + "구조")).isEqualTo("자료구조");   // 줄바꿈 없는 공백
		assertThat(NameNormalizer.normalize("자료" + (char) 0x3000 + "구조")).isEqualTo("자료구조");   // 전각 공백
		assertThat(NameNormalizer.normalize("자료\t\n구조")).isEqualTo("자료구조");       // 탭·줄바꿈
	}

	@Test
	void 문장부호는_남겨서_다른_과목은_다른_이름이다() {
		assertThat(NameNormalizer.normalize("C++ 프로그래밍")).isNotEqualTo(NameNormalizer.normalize("C 프로그래밍"));
		assertThat(NameNormalizer.normalize("C++ 프로그래밍")).isEqualTo("c++프로그래밍");
		assertThat(NameNormalizer.normalize("1주차")).isNotEqualTo(NameNormalizer.normalize("1주차."));
	}

	@Test
	void 공백과_보이지_않는_문자뿐이면_빈_문자열이다() {
		assertThat(NameNormalizer.normalize("   ")).isEmpty();
		assertThat(NameNormalizer.normalize("" + (char) 0x200B + (char) 0x200B + (char) 0xFEFF)).isEmpty();
		assertThat(NameNormalizer.normalize("")).isEmpty();
		assertThat(NameNormalizer.normalize(null)).isEmpty();
	}

}
