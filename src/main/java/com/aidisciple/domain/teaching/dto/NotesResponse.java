package com.aidisciple.domain.teaching.dto;

import java.time.Instant;
import java.util.List;

/** T-03 현재 노트(ACTIVE 만)를 주차별로 묶은 것. 개념별 묶음은 하지 않는다 (개념 목록은 학생에게 내려가지 않는다). */
public record NotesResponse(List<Group> groups) {

	/** {@code count}/{@code limit} 로 FE 가 "12/60"을 보여 준다. */
	public record Group(Unit unit, int count, int limit, List<Item> items) {
	}

	public record Unit(Long id, String title) {
	}

	public record Item(Long id, String content, Instant createdAt, int version, Long sourceMessageId) {
	}

}
