package com.aidisciple.domain.review.dto;

import java.util.List;

/** R-06 상대 노트. 상대가 준비 완료를 누른 순간의 스냅샷 노트를 그 순서대로 준다. */
public record OpponentNotesResponse(long battleId, Opponent opponent, List<Note> notes) {

	public record Opponent(long userId, String nickname) {
	}

	public record Note(long id, String content) {
	}

}
