package com.aidisciple.domain.material.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aidisciple.global.text.TextHash;
import com.aidisciple.global.text.TextNormalizer;

class SentencePreparerTest {

	@Test
	void 문장으로_나누고_1부터_순서를_매긴다() {
		SentencePreparer.Prepared prepared = SentencePreparer.prepare(List.of(
			new ExtractedPage(null, "스택은 나중에 넣은 것이 먼저 나오는 구조이다. 큐는 먼저 넣은 것이 먼저 나온다.")));

		assertThat(prepared.sentences()).extracting(PreparedSentence::seq).containsExactly(1, 2);
		assertThat(prepared.sentences()).extracting(PreparedSentence::text)
			.containsExactly("스택은 나중에 넣은 것이 먼저 나오는 구조이다.", "큐는 먼저 넣은 것이 먼저 나온다.");
	}

	@Test
	void 줄바꿈은_문장_경계라서_슬라이드의_줄이_각각_문장이다() {
		SentencePreparer.Prepared prepared = SentencePreparer.prepare(List.of(
			new ExtractedPage(1, "자료구조 3주차\n스택과 큐\n후입선출과 선입선출")));

		assertThat(prepared.sentences()).extracting(PreparedSentence::text)
			.containsExactly("자료구조 3주차", "스택과 큐", "후입선출과 선입선출");
	}

	@Test
	void 순서는_쪽을_넘어서_이어지고_문장마다_쪽수가_붙는다() {
		SentencePreparer.Prepared prepared = SentencePreparer.prepare(List.of(
			new ExtractedPage(1, "첫째 쪽의 문장이다.\n첫째 쪽의 둘째 줄이다."),
			new ExtractedPage(2, ""),
			new ExtractedPage(3, "셋째 쪽의 문장이다.")));

		assertThat(prepared.sentences()).extracting(PreparedSentence::seq).containsExactly(1, 2, 3);
		assertThat(prepared.sentences()).extracting(PreparedSentence::pageNo).containsExactly(1, 1, 3);
	}

	@Test
	void 붙여넣은_글은_쪽수가_없다() {
		SentencePreparer.Prepared prepared = SentencePreparer.prepare(List.of(new ExtractedPage(null, "쪽수가 없는 글이다.")));

		assertThat(prepared.sentences().get(0).pageNo()).isNull();
	}

	@Test
	void 정규화본과_해시는_가르치기_검증과_같은_규칙으로_만든다() {
		PreparedSentence sentence = SentencePreparer.prepare(List.of(
			new ExtractedPage(null, "Stack은  LIFO(후입선출) 구조다!"))).sentences().get(0);

		assertThat(sentence.normalizedText()).isEqualTo(TextNormalizer.normalize("Stack은  LIFO(후입선출) 구조다!"));
		assertThat(sentence.textHash()).isEqualTo(TextHash.sha256Hex(sentence.normalizedText())).hasSize(64);
	}

	@Test
	void 공백과_문장부호만_다른_문장은_같은_해시다() {
		PreparedSentence a = SentencePreparer.prepare(List.of(new ExtractedPage(null, "스택은 후입선출 구조다."))).sentences().get(0);
		PreparedSentence b = SentencePreparer.prepare(List.of(new ExtractedPage(null, "스택은  후입선출  구조다!"))).sentences().get(0);

		assertThat(a.textHash()).isEqualTo(b.textHash());
	}

	@Test
	void 글자_수는_공백을_뺀_수다() {
		SentencePreparer.Prepared prepared = SentencePreparer.prepare(List.of(
			new ExtractedPage(1, "가 나\n다 라"), new ExtractedPage(2, "마\t바")));

		assertThat(prepared.charCount()).isEqualTo(6);
	}

	@Test
	void 글이_없으면_문장도_글자_수도_없다() {
		SentencePreparer.Prepared prepared = SentencePreparer.prepare(List.of(
			new ExtractedPage(1, ""), new ExtractedPage(2, "  \n \n")));

		assertThat(prepared.sentences()).isEmpty();
		assertThat(prepared.charCount()).isZero();
		assertThat(SentencePreparer.prepare(List.of()).sentences()).isEmpty();
	}

}
