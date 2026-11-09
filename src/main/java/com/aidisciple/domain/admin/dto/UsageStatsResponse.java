package com.aidisciple.domain.admin.dto;

import java.time.LocalDate;

/**
 * X-11 시험 사용 지표. 기간은 {@code zone} 기준 날짜 {@code from}~{@code to}.
 * returningStudents = 기간 안에 서로 다른 이틀 이상 활동(가르치기 메시지 또는 배틀 참가)한 학생 수.
 */
public record UsageStatsResponse(
	String zone,
	LocalDate from,
	LocalDate to,
	long activeStudents,
	long returningStudents,
	long studentMessages,
	long rankedBattlesFinished,
	long practiceBattlesFinished,
	PracticeRetest practiceRetest,
	Reviews reviews,
	Reports reports,
	long answersRevealed
) {

	/** 연습 배틀에서 다시 푼 답. improvedFromWrong = 원래 틀렸던(맞히지 못한) 답이 이번엔 정답 */
	public record PracticeRetest(long answered, long correct, long improvedFromWrong, Double correctRate) {
	}

	/** causeDisagreed = 학생이 고른 원인이 서버 제안과 다른 복기 (오진단 후보) */
	public record Reviews(long written, long causeDisagreed) {
	}

	public record Reports(long question, long grading) {
	}

}
