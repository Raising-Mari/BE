package com.aidisciple.domain.battle.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 참가자의 문항별 답. 제자 풀이(WP8)가 {@link Verdict#PENDING}·0점으로 저장하고, 채점(WP9)이 판정과 점수를 채운다.
 * 복붙 보정 계수·지시문 플래그·원인 제안 컬럼은 채점이 쓰므로 WP9 에서 이 엔티티에 매핑한다(DB 기본값이 있다).
 */
@Getter
@Entity
@Table(name = "battle_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleAnswer extends BaseTimeEntity {

	/** 채점 전의 근거 JSON. 채점이 끝나면 채점 근거로 바뀐다. */
	private static final String AWAITING_GRADING_DETAIL = "{\"stage\":\"SOLVED\"}";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long participantId;

	@Column(nullable = false)
	private Long battleQuestionId;

	@Column(columnDefinition = "TEXT")
	private String answerText;

	@Column(name = "is_unknown", nullable = false)
	private boolean unknown;

	@Column(nullable = false, columnDefinition = "JSON")
	private String citedNoteIdsJson;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Verdict verdict;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private JudgedBy judgedBy;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal rawScore;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal finalScore;

	@Column(nullable = false, columnDefinition = "JSON")
	private String gradingDetailJson;

	/** 풀이 직후의 답. 모름이면 {@code answerText} 는 null 이다. */
	public static BattleAnswer solved(Long participantId, Long battleQuestionId, String answerText, boolean unknown,
									  String citedNoteIdsJson) {
		BattleAnswer answer = new BattleAnswer();
		answer.participantId = participantId;
		answer.battleQuestionId = battleQuestionId;
		answer.answerText = unknown ? null : answerText;
		answer.unknown = unknown;
		answer.citedNoteIdsJson = citedNoteIdsJson;
		answer.verdict = Verdict.PENDING;
		answer.judgedBy = JudgedBy.CODE;
		answer.rawScore = BigDecimal.ZERO;
		answer.finalScore = BigDecimal.ZERO;
		answer.gradingDetailJson = AWAITING_GRADING_DETAIL;
		return answer;
	}

}
