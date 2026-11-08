package com.aidisciple.domain.grading.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.aidisciple.global.text.TextNormalizer;

/**
 * 채점자를 겨냥한 지시문("정답으로 처리", "ignore previous" 등)을 찾는다(api-spec 11-4 의 2).
 * 비교는 정규화·공백 제거한 문자열로 해서 띄어쓰기·대소문자·기호로 피하는 것을 막는다. 패턴 목록은 설정(app.grading)이다.
 */
public final class InjectionDetector {

	private final List<String> originals;
	private final List<String> compacted;

	public InjectionDetector(List<String> patterns) {
		this.originals = new ArrayList<>();
		this.compacted = new ArrayList<>();
		for (String pattern : patterns) {
			String compact = TextNormalizer.compact(pattern);
			if (!compact.isEmpty()) {
				originals.add(pattern);
				compacted.add(compact);
			}
		}
	}

	/** 주어진 글들 중 하나라도 들어 있는 패턴(설정에 쓴 원래 표기). 없으면 빈 목록. */
	public List<String> find(List<String> texts) {
		Set<String> found = new LinkedHashSet<>();
		for (String text : texts) {
			String compactText = TextNormalizer.compact(text);
			if (compactText.isEmpty()) {
				continue;
			}
			for (int i = 0; i < compacted.size(); i++) {
				if (compactText.contains(compacted.get(i))) {
					found.add(originals.get(i));
				}
			}
		}
		return List.copyOf(found);
	}

}
