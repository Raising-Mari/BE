package com.aidisciple.domain.question.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SentenceWindowsTest {

	private static final List<SourceSentence> SENTENCES = List.of(
		new SourceSentence(1, 11L, "가".repeat(9)),
		new SourceSentence(2, 12L, "나".repeat(9)),
		new SourceSentence(3, 13L, "다".repeat(9)));

	@Test
	void 글자_수_상한에_맞춰_구간을_나눈다() {
		// 문장 하나가 10자(9자 + 줄바꿈 1)라 상한 15면 두 문장(20자)이 들어가지 못해 한 구간에 한 문장씩 들어간다
		assertThat(SentenceWindows.split(SENTENCES, 15)).hasSize(3);
		assertThat(SentenceWindows.split(SENTENCES, 1000)).hasSize(1);
	}

	@Test
	void 라운드마다_다음_구간을_주고_끝나면_처음으로_돌아간다() {
		assertThat(SentenceWindows.slice(SENTENCES, 15, 0)).extracting(SourceSentence::no).containsExactly(1);
		assertThat(SentenceWindows.slice(SENTENCES, 15, 1)).extracting(SourceSentence::no).containsExactly(2);
		assertThat(SentenceWindows.slice(SENTENCES, 15, 3)).extracting(SourceSentence::no).containsExactly(1);
	}

	@Test
	void 문장이_없으면_빈_구간이다() {
		assertThat(SentenceWindows.slice(List.of(), 15, 0)).isEmpty();
	}

}
