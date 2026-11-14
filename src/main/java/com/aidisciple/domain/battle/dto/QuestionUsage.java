package com.aidisciple.domain.battle.dto;

/** 관리자 문항 통계(X-03·X-04). 배틀에 나온 횟수와 채점된 답 중 정답 비율. 답이 없으면 정답률은 null 이다. */
public record QuestionUsage(long questionId, long usedCount, long answeredCount, long correctCount) {

	public static QuestionUsage none(long questionId) {
		return new QuestionUsage(questionId, 0, 0, 0);
	}

	/** 0.0~1.0, 소수 셋째 자리까지. 채점된 답이 없으면 null */
	public Double correctRate() {
		if (answeredCount == 0) {
			return null;
		}
		return Math.round(correctCount * 1000.0 / answeredCount) / 1000.0;
	}

}
