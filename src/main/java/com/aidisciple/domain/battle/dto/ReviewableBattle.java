package com.aidisciple.domain.battle.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.review.entity.ReviewCause;

/**
 * 복기(R-01)가 읽는 끝난 랭킹 배틀의 내 답들. 학생에게 그대로 내려가는 응답이 아니다 —
 * 모범 답·근거를 언제 보여 줄지는 복기 도메인이 정한다. 채점 근거 JSON(내부 패턴 목록)은 담지 않는다.
 */
public record ReviewableBattle(long battleId, Instant answersRevealedAt, List<Answer> answers) {

	public boolean answersRevealed() {
		return answersRevealedAt != null;
	}

	/** seq 순. {@code citedNoteIds} 는 제자가 이 답에서 인용한 노트(준비 순간의 스냅샷 안의 노트) */
	public record Answer(long battleAnswerId, int seq, long questionId, String answerText, boolean unknown,
						 Verdict verdict, BigDecimal finalScore, BigDecimal copyFactor, boolean injectionFlag,
						 ReviewCause causeSuggestion, List<Long> citedNoteIds) {
	}

}
