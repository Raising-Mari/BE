package com.aidisciple.domain.battle.logic;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code battle_participants.note_ids_json}·{@code battle_answers.cited_note_ids_json} 의 모양: 숫자 배열 {@code [12,15]}.
 * 숫자만 담으므로 JSON 라이브러리 없이 읽고 쓴다.
 */
public final class NoteIdsJson {

	private NoteIdsJson() {
	}

	public static String write(Collection<Long> ids) {
		return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
	}

	/** null·빈 값·{@code []} 는 빈 목록이다. */
	public static List<Long> read(String json) {
		if (json == null) {
			return List.of();
		}
		String body = json.strip();
		if (body.startsWith("[")) {
			body = body.substring(1);
		}
		if (body.endsWith("]")) {
			body = body.substring(0, body.length() - 1);
		}
		body = body.strip();
		if (body.isEmpty()) {
			return List.of();
		}
		return java.util.Arrays.stream(body.split(","))
			.map(String::strip)
			.map(Long::valueOf)
			.toList();
	}

}
