package com.aidisciple.domain.battle.dto;

import java.math.BigDecimal;
import java.util.List;

import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.Verdict;

/**
 * B-07 결과 (끝난 배틀만). 점수는 소수다(부분 정답·감점·복붙 보정). {@code elo} 는 랭킹 배틀만 있다.
 * 모범 답과 자료 근거는 싣지 않는다 — 원본 배틀의 R-01 에서 정답 보기(R-11) 뒤에 본다.
 * 연습 배틀은 {@code opponent}·{@code result}·{@code elo} 가 null 이고 {@code sourceBattleId} 가 있다.
 */
public record BattleResultResponse(
	BattleMode mode,
	Long sourceBattleId,
	ScoreSummary me,
	ScoreSummary opponent,
	BattleResult result,
	Elo elo,
	List<QuestionResult> questions
) {

	public record ScoreSummary(BigDecimal totalScore, int correct, int partial, int wrong, int unknown) {
	}

	public record Elo(int before, int after, int delta) {
	}

	/**
	 * {@code opponentAnswerText} 는 두 사람이 모두 노트 공개에 동의했을 때만 있다.
	 * {@code retestOf} 는 연습 배틀에서 이 문항이 다시 시험하는 이전 답이다.
	 */
	public record QuestionResult(
		int seq,
		String stem,
		String myAnswerText,
		Verdict myVerdict,
		BigDecimal myScore,
		String opponentAnswerText,
		Verdict opponentVerdict,
		BigDecimal opponentScore,
		RetestOf retestOf
	) {
	}

	public record RetestOf(Long battleAnswerId, Verdict verdict) {
	}

}
