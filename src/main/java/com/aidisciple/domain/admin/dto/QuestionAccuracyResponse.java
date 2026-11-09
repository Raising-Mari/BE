package com.aidisciple.domain.admin.dto;

import java.util.List;

/**
 * X-09 정답률이 0%·100%에 가까운 문항. 랭킹 배틀에서 채점된 답이 {@code minAnswers} 개 이상인 문항만 본다.
 * {@code tooHard} 는 정답률 ≤ {@code tooHardAtMost}, {@code tooEasy} 는 ≥ {@code tooEasyAtLeast}.
 */
public record QuestionAccuracyResponse(int minAnswers, double tooHardAtMost, double tooEasyAtLeast,
									   List<Item> tooHard, List<Item> tooEasy) {

	public record Item(long questionId, long unitId, String stem, String status, long answeredCount, long correctCount,
					   double correctRate) {
	}

}
