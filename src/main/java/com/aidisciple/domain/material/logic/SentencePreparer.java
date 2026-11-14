package com.aidisciple.domain.material.logic;

import java.util.ArrayList;
import java.util.List;

import com.aidisciple.global.text.SentenceSplitter;
import com.aidisciple.global.text.TextHash;
import com.aidisciple.global.text.TextNormalizer;

/**
 * 추출한 글을 저장할 문장 목록으로 바꾼다. 문장 분리·정규화·해시는 가르치기·근거 확인·복붙 보정이 함께 쓰는
 * {@code global/text} 의 것을 그대로 써서, 나중에 서로 같은 문장을 같은 값으로 찾을 수 있게 한다.
 *
 * <p>문장은 쪽 경계를 넘지 않는다(한 문장이 두 쪽에 걸쳐도 쪽마다 따로 나뉜다). 그래야 문장마다 쪽수를 하나로 정할 수 있다.
 * 줄바꿈은 항상 문장 경계다. 슬라이드 자료에는 맞지만, 한 문단이 여러 줄로 접힌 문서 PDF 는 문장이 줄 단위로 잘릴 수 있다.
 */
public final class SentencePreparer {

	private SentencePreparer() {
	}

	/** @param charCount 공백을 뺀 글자 수. 스캔본 판정과 크기 제한에 쓴다. */
	public record Prepared(List<PreparedSentence> sentences, int charCount) {
	}

	public static Prepared prepare(List<ExtractedPage> pages) {
		List<PreparedSentence> sentences = new ArrayList<>();
		long charCount = 0;
		for (ExtractedPage page : pages) {
			charCount += countNonWhitespace(page.text());
			for (String sentence : SentenceSplitter.split(page.text())) {
				String normalized = TextNormalizer.normalize(sentence);
				if (normalized.isEmpty()) {
					continue;
				}
				sentences.add(new PreparedSentence(sentences.size() + 1, page.pageNo(), sentence, normalized,
					TextHash.sha256Hex(normalized)));
			}
		}
		return new Prepared(List.copyOf(sentences), (int) Math.min(charCount, Integer.MAX_VALUE));
	}

	private static long countNonWhitespace(String text) {
		return text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
	}

}
