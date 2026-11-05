package com.aidisciple.domain.review.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.review.entity.ReviewCause;

/**
 * R-01 복기 (끝난 RANKED 배틀만). 정답 보기(R-11) 전에는 {@code question.referenceAnswer} 와 {@code evidence} 를
 * null 로 두고 JSON 에서 필드 자체를 뺀다 — api-spec "학생에게 절대 내려가지 않는 것".
 */
public record ReviewResponse(boolean answersRevealed, List<Item> items) {

	/** 맞힌 문항(CORRECT)도 연습 대상으로 고를 수 있게 들어가고, 이때 {@code causeSuggestion} 은 null 이다. */
	public record Item(
		Long battleAnswerId,
		int seq,
		Question question,
		Disciple disciple,
		List<UsedNote> usedNotes,
		ReviewCause causeSuggestion,
		MyReview myReview,
		boolean practiceAvailable,
		@JsonInclude(JsonInclude.Include.NON_NULL) Evidence evidence
	) {
	}

	public record Question(
		Long id,
		String stem,
		@JsonInclude(JsonInclude.Include.NON_NULL) String referenceAnswer
	) {
	}

	/** ZEROED_INJECTION 이면 {@code injectionFlag=true} 만 알리고 어떤 패턴이 걸렸는지는 내려주지 않는다. */
	public record Disciple(
		String answerText,
		boolean isUnknown,
		Verdict verdict,
		BigDecimal finalScore,
		BigDecimal copyFactor,
		boolean injectionFlag
	) {
	}

	public record UsedNote(Long id, String content) {
	}

	public record MyReview(
		ReviewCause userCause,
		String studyMemo,
		String aiMemo,
		String sourceNote,
		String reasonNote
	) {
	}

	public record Evidence(String text, String materialTitle) {
	}

}
