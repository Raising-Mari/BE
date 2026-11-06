package com.aidisciple.domain.auth.logic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknamePolicyTest {

	@ParameterizedTest
	@ValueSource(strings = {"가나", "제자왕", "stack_master", "Queue2", "a_1", "열두글자닉네임입니다12", "시스템생명", "ab"})
	void 한글_영문_숫자_밑줄_2에서_12자는_쓸_수_있다(String nickname) {
		assertThat(NicknamePolicy.isValid(nickname)).isTrue();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "a", "가", "열세글자닉네임입니다123", "닉 네임", "닉네임!", "nick-name", "닉네임😀", "ㄱㄴㄷㄹ", " 닉네임"})
	void 길이_공백_특수문자_자모가_맞지_않으면_쓸_수_없다(String nickname) {
		assertThat(NicknamePolicy.isValid(nickname)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"admin", "ADMIN", "Admin01", "관리자", "관리자님", "운영자1", "운영진", "system", "시스템"})
	void 운영진을_사칭하는_이름은_쓸_수_없다(String nickname) {
		assertThat(NicknamePolicy.isValid(nickname)).isFalse();
	}

}
