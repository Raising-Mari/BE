package com.aidisciple.domain.question.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * 주차 문장을 글자 수 상한에 맞춰 구간으로 나눈다. 자료가 길어도 프롬프트가 넘치지 않게,
 * 라운드마다 다음 구간을 넣어 다른 내용으로 문항을 만들게 한다. 구간보다 라운드가 많으면 처음으로 돌아간다.
 */
public final class SentenceWindows {

	private SentenceWindows() {
	}

	public static List<SourceSentence> slice(List<SourceSentence> sentences, int maxChars, int round) {
		List<List<SourceSentence>> windows = split(sentences, maxChars);
		if (windows.isEmpty()) {
			return List.of();
		}
		return windows.get(Math.floorMod(round, windows.size()));
	}

	static List<List<SourceSentence>> split(List<SourceSentence> sentences, int maxChars) {
		List<List<SourceSentence>> windows = new ArrayList<>();
		List<SourceSentence> current = new ArrayList<>();
		int length = 0;
		for (SourceSentence sentence : sentences) {
			int added = sentence.text().length() + 1;
			// 문장 하나가 상한보다 길어도 버리지 않고 그 문장만으로 한 구간을 만든다
			if (!current.isEmpty() && length + added > maxChars) {
				windows.add(current);
				current = new ArrayList<>();
				length = 0;
			}
			current.add(sentence);
			length += added;
		}
		if (!current.isEmpty()) {
			windows.add(current);
		}
		return windows;
	}

}
