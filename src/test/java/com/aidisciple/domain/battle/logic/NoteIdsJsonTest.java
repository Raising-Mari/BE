package com.aidisciple.domain.battle.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class NoteIdsJsonTest {

	@Test
	void 숫자_배열로_쓰고_다시_읽는다() {
		assertThat(NoteIdsJson.write(List.of(12L, 15L, 3L))).isEqualTo("[12,15,3]");
		assertThat(NoteIdsJson.read("[12,15,3]")).containsExactly(12L, 15L, 3L);
		assertThat(NoteIdsJson.read(NoteIdsJson.write(List.of(1L)))).containsExactly(1L);
	}

	@Test
	void 빈_값은_빈_목록이다() {
		assertThat(NoteIdsJson.write(List.of())).isEqualTo("[]");
		assertThat(NoteIdsJson.read("[]")).isEmpty();
		assertThat(NoteIdsJson.read(null)).isEmpty();
		assertThat(NoteIdsJson.read("  ")).isEmpty();
	}

	@Test
	void 공백이_섞여_있어도_읽는다() {
		assertThat(NoteIdsJson.read(" [ 4 , 5 ] ")).containsExactly(4L, 5L);
	}

}
