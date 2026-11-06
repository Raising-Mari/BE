package com.aidisciple.domain.teaching.service;

import java.util.List;

import com.aidisciple.domain.teaching.entity.NoteOp;
import com.aidisciple.domain.teaching.logic.ProposedNoteChange;

/**
 * 프롬프트 {@code teaching-turn} 이 요구하는 JSON 응답 모양. AI 가 준 값이므로 검증하기 전에는 믿지 않는다.
 */
public record TeachingLlmResponse(String reply, List<LlmNoteChange> noteChanges) {

	public record LlmNoteChange(NoteOp op, Long targetId, String content, String evidence) {

		ProposedNoteChange toProposed() {
			return new ProposedNoteChange(op, targetId, content, evidence);
		}

	}

	/** 말이 비었거나 op 가 없는 변경이 섞여 있으면 형식이 맞지 않는 응답으로 본다. (재시도 대상) */
	boolean isUsable() {
		if (reply == null || reply.isBlank()) {
			return false;
		}
		return changes().stream().allMatch(change -> change != null && change.op() != null);
	}

	List<LlmNoteChange> changes() {
		return noteChanges == null ? List.of() : noteChanges;
	}

}
