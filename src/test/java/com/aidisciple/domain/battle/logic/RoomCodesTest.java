package com.aidisciple.domain.battle.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

class RoomCodesTest {

	@Test
	void 만든_코드는_6자리이고_헷갈리는_글자가_없다() {
		for (int i = 0; i < 500; i++) {
			String code = RoomCodes.generate();

			assertThat(code).hasSize(6);
			assertThat(code).matches("[A-HJKMNP-Z2-9]{6}");
		}
	}

	@Test
	void 같은_시드면_같은_코드다() {
		assertThat(RoomCodes.generate(new Random(7))).isEqualTo(RoomCodes.generate(new Random(7)));
	}

	@Test
	void 입력은_대소문자와_공백을_무시한다() {
		assertThat(RoomCodes.normalize(" k7m 4qx ")).isEqualTo("K7M4QX");
		assertThat(RoomCodes.normalize("k7m4qx\n")).isEqualTo("K7M4QX");
		assertThat(RoomCodes.normalize("K7M　4QX")).isEqualTo("K7M4QX");   // 전각 공백
	}

	@Test
	void 형식이_아니면_null이다() {
		assertThat(RoomCodes.normalize(null)).isNull();
		assertThat(RoomCodes.normalize("")).isNull();
		assertThat(RoomCodes.normalize("K7M4Q")).isNull();      // 5자리
		assertThat(RoomCodes.normalize("K7M4QXX")).isNull();    // 7자리
		assertThat(RoomCodes.normalize("K7M4Q0")).isNull();     // 0 은 쓰지 않는다
		assertThat(RoomCodes.normalize("K7M4Q-")).isNull();
	}

}
