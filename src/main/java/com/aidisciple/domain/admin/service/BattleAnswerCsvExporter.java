package com.aidisciple.domain.admin.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * X-12 채점 일치도 비교용 CSV. 끝난 배틀의 답마다 한 줄: 문항·모범 답·제자의 답·판정·점수·원인 제안·학생이 고른 원인.
 * 사람이 같은 답을 다시 채점해 서버 판정과 맞춰 보는 용도다. <b>학생을 알아볼 수 있는 값(id·이름·메일)은 넣지 않는다.</b>
 * 엑셀에서 한글이 깨지지 않게 UTF-8 BOM 을 붙이고, 수식으로 실행되지 않게(CSV 인젝션) =,+,-,@ 로 시작하는 글은 앞에 ' 를 붙인다.
 */
@Service
@RequiredArgsConstructor
public class BattleAnswerCsvExporter {

	private static final String[] HEADER = {"battle_id", "mode", "finished_at", "answer_id", "question_id", "grading_mode",
		"stem", "reference_answer", "answer_text", "is_unknown", "verdict", "judged_by", "raw_score", "final_score",
		"copy_ratio", "injection_flag", "cause_suggestion", "user_cause"};

	private static final String SQL = """
		SELECT b.id AS battle_id, b.mode, b.finished_at, a.id AS answer_id, q.id AS question_id, q.grading_mode,
		       q.stem, q.reference_answer, a.answer_text, a.is_unknown, a.verdict, a.judged_by, a.raw_score,
		       a.final_score, a.copy_ratio, a.injection_flag, a.cause_suggestion, r.user_cause
		FROM battle_answers a
		JOIN battle_questions bq ON bq.id = a.battle_question_id
		JOIN battles b ON b.id = bq.battle_id
		JOIN questions q ON q.id = bq.question_id
		LEFT JOIN review_entries r ON r.battle_answer_id = a.id
		WHERE b.status = 'FINISHED' AND b.finished_at >= :since
		  AND (:courseId IS NULL OR b.course_id = :courseId)
		ORDER BY b.id, bq.seq, a.id
		""";

	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	/** 응답을 쓰기 전에 먼저 검사한다(오류를 CSV 가 아니라 JSON 으로 돌려주려고) */
	public static int resolveDays(Integer days) {
		int dayCount = days == null ? 30 : days;
		if (dayCount < 1 || dayCount > 365) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "days 는 1~365 사이여야 합니다.");
		}
		return dayCount;
	}

	@Transactional(readOnly = true)
	public void write(Writer writer, int days, Long courseId) throws IOException {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("since", Timestamp.from(clock.instant().minus(Duration.ofDays(days))))
			.addValue("courseId", courseId, Types.BIGINT);
		writer.write('\uFEFF');
		writeRow(writer, HEADER);
		try {
			jdbc.query(SQL, params, (RowCallbackHandler) rs -> writeRow(writer, row(rs)));
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
		writer.flush();
	}

	private static String[] row(ResultSet rs) throws SQLException {
		Timestamp finishedAt = rs.getTimestamp("finished_at");
		return new String[] {
			rs.getString("battle_id"), rs.getString("mode"), finishedAt == null ? "" : finishedAt.toInstant().toString(),
			rs.getString("answer_id"), rs.getString("question_id"), rs.getString("grading_mode"),
			text(rs.getString("stem")), text(rs.getString("reference_answer")), text(rs.getString("answer_text")),
			String.valueOf(rs.getBoolean("is_unknown")), rs.getString("verdict"), rs.getString("judged_by"),
			rs.getString("raw_score"), rs.getString("final_score"), rs.getString("copy_ratio"),
			String.valueOf(rs.getBoolean("injection_flag")), rs.getString("cause_suggestion"), rs.getString("user_cause")};
	}

	/** 사람이 쓴 글(문항·답)만: 수식으로 실행되지 않게 막는다. 숫자 칸(음수 점수)에는 쓰지 않는다. */
	static String text(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		char first = value.charAt(0);
		return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
			? "'" + value : value;
	}

	private static void writeRow(Writer writer, String[] cells) {
		try {
			for (int i = 0; i < cells.length; i++) {
				if (i > 0) {
					writer.write(',');
				}
				writer.write(escape(cells[i]));
			}
			writer.write("\r\n");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

}
