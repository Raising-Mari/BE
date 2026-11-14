package com.aidisciple.ai.log;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * X-10 AI 호출 통계 (무료 티어 한도 감시). 시험 사용 규모에서는 한 달치 기록도 많지 않아,
 * DB 별 날짜 함수 대신 기록을 읽어 와서 한국 시간 날짜로 묶는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiCallStatsService {

	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	static final int DEFAULT_DAYS = 7;
	static final int MAX_DAYS = 31;

	private final AiCallLogRepository repository;
	private final Clock clock;

	/** 오늘(한국 시간)을 포함한 최근 {@code days}일 */
	public AiCallStatsResponse daily(Integer days) {
		int dayCount = days == null ? DEFAULT_DAYS : days;
		if (dayCount < 1 || dayCount > MAX_DAYS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "days 는 1~31 사이여야 합니다.");
		}
		LocalDate today = LocalDate.now(clock.withZone(ZONE));
		LocalDate from = today.minusDays(dayCount - 1L);

		// 최근 날짜부터, 호출이 없는 날도 0 으로 보이게 미리 채운다
		Map<LocalDate, Counter> counters = new LinkedHashMap<>();
		for (LocalDate date = today; !date.isBefore(from); date = date.minusDays(1)) {
			counters.put(date, new Counter());
		}
		Instant since = from.atStartOfDay(ZONE).toInstant();
		for (Object[] row : repository.findForStats(since)) {
			LocalDate date = LocalDate.ofInstant((Instant) row[2], ZONE);
			Counter counter = counters.get(date);
			if (counter != null) {
				counter.add((AiPurpose) row[0], (Boolean) row[1], (Integer) row[3], (Integer) row[4]);
			}
		}
		List<AiCallStatsResponse.Day> result = new ArrayList<>();
		counters.forEach((date, counter) -> result.add(counter.toDay(date)));
		return new AiCallStatsResponse(ZONE.getId(), from, today, result);
	}

	/** 하루치 합계 */
	private static final class Counter {

		private long total;
		private long failed;
		private long inputTokens;
		private long outputTokens;
		private final Map<AiPurpose, Long> byPurpose = new EnumMap<>(AiPurpose.class);

		void add(AiPurpose purpose, Boolean success, Integer input, Integer output) {
			total++;
			if (!Boolean.TRUE.equals(success)) {
				failed++;
			}
			inputTokens += input == null ? 0 : input;
			outputTokens += output == null ? 0 : output;
			byPurpose.merge(purpose, 1L, Long::sum);
		}

		AiCallStatsResponse.Day toDay(LocalDate date) {
			return new AiCallStatsResponse.Day(date, total, failed, inputTokens, outputTokens, Map.copyOf(byPurpose));
		}

	}

}
