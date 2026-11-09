package com.aidisciple.ai.log;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.aidisciple.ai.AiPurpose;

/** X-10 일·용도별 AI 호출 수. 최근 날짜가 앞이고, 호출이 없는 날도 0 으로 나온다. 날짜는 {@code zone} 기준이다. */
public record AiCallStatsResponse(String zone, LocalDate from, LocalDate to, List<Day> days) {

	/** {@code byPurpose} 에는 호출이 있었던 용도만 들어간다 */
	public record Day(LocalDate date, long total, long failed, long inputTokens, long outputTokens,
					  Map<AiPurpose, Long> byPurpose) {
	}

}
