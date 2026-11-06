package com.aidisciple.domain.auth.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailMaskerTest {

	@Test
	void 앞_한_글자와_도메인만_남긴다() {
		assertThat(EmailMasker.mask("abc@mju.ac.kr")).isEqualTo("a***@mju.ac.kr");
		assertThat(EmailMasker.mask("a@mju.ac.kr")).isEqualTo("a***@mju.ac.kr");
	}

	@Test
	void 한글_아이디도_한_글자만_남긴다() {
		assertThat(EmailMasker.mask("가나다@mju.ac.kr")).isEqualTo("가***@mju.ac.kr");
	}

	@Test
	void 이메일_형식이_아니면_전부_가린다() {
		assertThat(EmailMasker.mask(null)).isEqualTo("***");
		assertThat(EmailMasker.mask("")).isEqualTo("***");
		assertThat(EmailMasker.mask("no-at-sign")).isEqualTo("***");
		assertThat(EmailMasker.mask("@mju.ac.kr")).isEqualTo("***");
	}

}
