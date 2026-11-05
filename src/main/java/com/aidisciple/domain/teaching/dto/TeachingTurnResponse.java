package com.aidisciple.domain.teaching.dto;

import java.time.Instant;
import java.util.List;

import com.aidisciple.domain.teaching.entity.NoteOp;

/** T-01 가르치기 한 턴의 결과 (api-spec 4절, 처리 절차는 11-1) */
public record TeachingTurnResponse(
	int turnNo,
	StudentMessage studentMessage,
	DiscipleMessage discipleMessage,
	NoteChanges noteChanges,
	boolean pasteWarning
) {

	public record StudentMessage(Long id, String content, Instant createdAt) {
	}

	/** {@code askedWhy}: 서버가 3턴마다 제자에게 "왜/어떻게"를 되묻게 시킨 턴이면 true */
	public record DiscipleMessage(Long id, String content, boolean askedWhy, Instant createdAt) {
	}

	/**
	 * {@code rejectedCount}: 서버 검증에서 탈락한 변경 수(이유는 학생에게 내려주지 않는다).
	 * {@code noteLimitReached}: 주차당 상한(60개)이라 이번 턴의 ADD 가 거부됐다.
	 */
	public record NoteChanges(
		List<AppliedChange> applied,
		List<MergedNote> merged,
		List<MergeSuggestion> mergeSuggestions,
		int rejectedCount,
		boolean noteLimitReached
	) {
	}

	/** UPDATE 일 때만 {@code replacesEntryId} 가 있고, REMOVE 는 {@code content} 가 없다. */
	public record AppliedChange(NoteOp op, Long entryId, Long replacesEntryId, String content) {
	}

	/** 유사도 ≥ 0.9 라 자동 병합된 옛 노트와 그것을 흡수한 새 노트 */
	public record MergedNote(Long entryId, Long intoEntryId) {
	}

	/** 유사도 0.75 이상 0.9 미만이라 학생에게 "합칠까요?"를 물을 쌍. 서버는 저장하지 않는다. */
	public record MergeSuggestion(Long newEntryId, Long existingEntryId, String existingContent, double similarity) {
	}

}
