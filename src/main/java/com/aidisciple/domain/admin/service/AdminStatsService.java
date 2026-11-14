package com.aidisciple.domain.admin.service;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.admin.dto.QuestionAccuracyResponse;
import com.aidisciple.domain.admin.dto.UsageStatsResponse;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 관리자 통계 X-09·X-11. 여러 도메인 테이블을 한꺼번에 세는 <b>읽기 전용</b> 집계라, 도메인 창구를 여러 개 만드는 대신
 * SQL 로 직접 센다(쓰기는 하지 않는다). 개인을 알아볼 수 있는 값(이름·메일·대화 내용)은 내보내지 않고 숫자만 낸다.
 * DB 시각은 UTC 이고, 날짜는 한국 시간으로 묶는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	static final double TOO_HARD_AT_MOST = 0.1;
	static final double TOO_EASY_AT_LEAST = 0.9;

	private static final String ACCURACY_SQL = """
		SELECT q.id, q.unit_id, q.stem, q.status, COUNT(a.id) AS answered,
		       SUM(CASE WHEN a.verdict = 'CORRECT' THEN 1 ELSE 0 END) AS correct
		FROM battle_answers a
		JOIN battle_questions bq ON bq.id = a.battle_question_id
		JOIN battles b ON b.id = bq.battle_id
		JOIN questions q ON q.id = bq.question_id
		WHERE b.mode = 'RANKED' AND a.verdict <> 'PENDING'
		  AND (:unitId IS NULL OR q.unit_id = :unitId)
		GROUP BY q.id
		HAVING COUNT(a.id) >= :minAnswers
		ORDER BY q.id
		""";

	/** 활동 = 학생이 보낸 가르치기 메시지 또는 배틀 참가. 날짜는 한국 시간(UTC+9) */
	private static final String ACTIVITY_SQL = """
		SELECT d.user_id AS user_id, DATE(DATE_ADD(m.created_at, INTERVAL 9 HOUR)) AS day
		FROM chat_messages m JOIN disciples d ON d.id = m.disciple_id
		WHERE m.role = 'STUDENT' AND m.created_at >= :since
		UNION ALL
		SELECT p.user_id, DATE(DATE_ADD(p.created_at, INTERVAL 9 HOUR))
		FROM battle_participants p WHERE p.created_at >= :since
		""";

	private static final String PRACTICE_SQL = """
		SELECT COUNT(*) AS answered,
		       COALESCE(SUM(a.verdict = 'CORRECT'), 0) AS correct,
		       COALESCE(SUM(src.verdict <> 'CORRECT' AND a.verdict = 'CORRECT'), 0) AS improved
		FROM battle_answers a
		JOIN battle_questions bq ON bq.id = a.battle_question_id
		JOIN battle_answers src ON src.id = bq.source_answer_id
		WHERE a.verdict <> 'PENDING' AND a.created_at >= :since
		""";

	private static final String REVIEWS_SQL = """
		SELECT COUNT(*) AS written,
		       COALESCE(SUM(a.cause_suggestion IS NOT NULL AND r.user_cause <> a.cause_suggestion), 0) AS disagreed
		FROM review_entries r JOIN battle_answers a ON a.id = r.battle_answer_id
		WHERE r.created_at >= :since
		""";

	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	/** X-09 */
	public QuestionAccuracyResponse questionAccuracy(Long unitId, Integer minAnswers) {
		int min = minAnswers == null ? 5 : minAnswers;
		if (min < 1 || min > 1000) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "minAnswers 는 1~1000 사이여야 합니다.");
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("unitId", unitId, Types.BIGINT)
			.addValue("minAnswers", min);
		List<QuestionAccuracyResponse.Item> items = jdbc.query(ACCURACY_SQL, params, (rs, rowNum) -> {
			long answered = rs.getLong("answered");
			long correct = rs.getLong("correct");
			return new QuestionAccuracyResponse.Item(rs.getLong("id"), rs.getLong("unit_id"), rs.getString("stem"),
				rs.getString("status"), answered, correct, Math.round(correct * 1000.0 / answered) / 1000.0);
		});
		return new QuestionAccuracyResponse(min, TOO_HARD_AT_MOST, TOO_EASY_AT_LEAST,
			items.stream().filter(item -> item.correctRate() <= TOO_HARD_AT_MOST).toList(),
			items.stream().filter(item -> item.correctRate() >= TOO_EASY_AT_LEAST).toList());
	}

	/** X-11: 오늘(한국 시간)을 포함한 최근 {@code days}일 */
	public UsageStatsResponse usage(Integer days) {
		int dayCount = days == null ? 7 : days;
		if (dayCount < 1 || dayCount > 90) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "days 는 1~90 사이여야 합니다.");
		}
		LocalDate today = LocalDate.now(clock.withZone(ZONE));
		LocalDate from = today.minusDays(dayCount - 1L);
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("since", Timestamp.from(from.atStartOfDay(ZONE).toInstant()));

		long active = count("SELECT COUNT(DISTINCT user_id) FROM (" + ACTIVITY_SQL + ") t", params);
		long returning = count("SELECT COUNT(*) FROM (SELECT user_id FROM (" + ACTIVITY_SQL
			+ ") t GROUP BY user_id HAVING COUNT(DISTINCT day) >= 2) r", params);
		long messages = count("SELECT COUNT(*) FROM chat_messages WHERE role = 'STUDENT' AND created_at >= :since", params);
		Map<String, Long> battles = countBy(
			"SELECT mode, COUNT(*) FROM battles WHERE status = 'FINISHED' AND finished_at >= :since GROUP BY mode", params);
		Map<String, Long> reports = countBy(
			"SELECT kind, COUNT(*) FROM reports WHERE created_at >= :since GROUP BY kind", params);
		long revealed = count("SELECT COUNT(*) FROM battle_participants WHERE answers_revealed_at >= :since", params);

		UsageStatsResponse.PracticeRetest practice = jdbc.queryForObject(PRACTICE_SQL, params, (rs, rowNum) -> {
			long answered = rs.getLong("answered");
			long correct = rs.getLong("correct");
			return new UsageStatsResponse.PracticeRetest(answered, correct, rs.getLong("improved"),
				answered == 0 ? null : Math.round(correct * 1000.0 / answered) / 1000.0);
		});
		UsageStatsResponse.Reviews reviews = jdbc.queryForObject(REVIEWS_SQL, params,
			(rs, rowNum) -> new UsageStatsResponse.Reviews(rs.getLong("written"), rs.getLong("disagreed")));

		return new UsageStatsResponse(ZONE.getId(), from, today, active, returning, messages,
			battles.getOrDefault("RANKED", 0L), battles.getOrDefault("PRACTICE", 0L), practice, reviews,
			new UsageStatsResponse.Reports(reports.getOrDefault("QUESTION", 0L), reports.getOrDefault("GRADING", 0L)),
			revealed);
	}

	private long count(String sql, MapSqlParameterSource params) {
		Long value = jdbc.queryForObject(sql, params, Long.class);
		return value == null ? 0 : value;
	}

	/** 첫 열 → 둘째 열(개수) */
	private Map<String, Long> countBy(String sql, MapSqlParameterSource params) {
		Map<String, Long> result = new HashMap<>();
		jdbc.query(sql, params, rs -> {
			result.put(rs.getString(1), rs.getLong(2));
		});
		return result;
	}

}
