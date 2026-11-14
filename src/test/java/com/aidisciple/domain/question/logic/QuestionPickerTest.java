package com.aidisciple.domain.question.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class QuestionPickerTest {

	private static List<QuestionPicker.Candidate> withoutConcepts(int size) {
		return LongStream.rangeClosed(1, size).mapToObj(id -> new QuestionPicker.Candidate(id, null)).toList();
	}

	@Test
	void 같은_시드면_같은_세트가_나온다() {
		List<QuestionPicker.Candidate> candidates = withoutConcepts(20);
		assertThat(QuestionPicker.pick(candidates, 5, 9L)).isEqualTo(QuestionPicker.pick(candidates, 5, 9L));
		assertThat(QuestionPicker.pick(candidates, 5, 9L)).hasSize(5).doesNotHaveDuplicates();
	}

	@Test
	void 개념이_겹치지_않게_먼저_고른다() {
		List<QuestionPicker.Candidate> candidates = List.of(
			new QuestionPicker.Candidate(1, 100L), new QuestionPicker.Candidate(2, 100L),
			new QuestionPicker.Candidate(3, 100L), new QuestionPicker.Candidate(4, 200L));
		// 개념이 둘뿐이라 2개를 고르면 반드시 개념 100 하나와 200 하나다
		List<Long> picked = QuestionPicker.pick(candidates, 2, 1L);
		assertThat(picked).contains(4L).hasSize(2);
	}

	@Test
	void 개념이_모자라면_겹침을_허용한다() {
		List<QuestionPicker.Candidate> candidates = List.of(
			new QuestionPicker.Candidate(1, 100L), new QuestionPicker.Candidate(2, 100L),
			new QuestionPicker.Candidate(3, 100L));
		assertThat(QuestionPicker.pick(candidates, 3, 1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
	}

	@Test
	void 후보가_모자라면_빈_목록이다() {
		assertThat(QuestionPicker.pick(withoutConcepts(4), 5, 1L)).isEmpty();
	}

}
