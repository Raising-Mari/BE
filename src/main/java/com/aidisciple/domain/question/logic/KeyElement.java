package com.aidisciple.domain.question.logic;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.aidisciple.global.text.TextNormalizer;

/** 핵심 요소 하나. DB {@code questions.key_elements_json} 의 원소와 모양이 같다: {"label":"후입선출","aliases":["LIFO"]} */
public record KeyElement(String label, List<String> aliases) {

	public KeyElement {
		aliases = aliases == null ? List.of() : aliases.stream().filter(Objects::nonNull).toList();
	}

	/** compactText(TextNormalizer.compact 결과)에 이 요소의 이름이나 별칭이 들어 있는가 */
	public boolean foundIn(String compactText) {
		return Stream.concat(Stream.of(label), aliases.stream())
			.map(TextNormalizer::compact)
			.anyMatch(term -> !term.isEmpty() && compactText.contains(term));
	}

}
