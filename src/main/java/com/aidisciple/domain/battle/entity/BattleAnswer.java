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

import com.aidisciple.domain.review.entity.ReviewCause;
import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 참가자의 문항별 답. 제자 풀이(WP8)가 {@link Verdict#PENDING}·0점으로 저장하고, 채점(WP9)이 판정과 점수를 채운다.
 * 복붙 비율·보정 계수·지시문 플래그·원인 제안은 채점이 {@link #applyGrade} 로 채운다.
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

	@Column(nullable = false, precision = 4, scale = 3)
	private BigDecimal copyRatio = BigDecimal.ZERO;

	@Column(nullable = false, precision = 3, scale = 2)
	private BigDecimal copyFactor = BigDecimal.ONE;

	@Column(nullable = false)
	private boolean injectionFlag;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private ReviewCause causeSuggestion;

	@Column(nullable = false, columnDefinition = "JSON")
	private String gradingDetailJson;

	public boolean isPending() {
		return verdict == Verdict.PENDING;
	}

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

	/**
	 * 채점 결과를 채운다(WP9). 채점 전(PENDING)인 답에만 할 수 있다 — 한 번 채점한 답은 다시 바꾸지 않는다.
	 *
	 * @param causeSuggestion WRONG·PARTIAL·UNKNOWN·UNJUDGED 일 때만 있다. 제안일 뿐 최종 원인은 학생이 고른다(R-02).
	 */
	public void applyGrade(Verdict verdict, JudgedBy judgedBy, BigDecimal rawScore, BigDecimal copyRatio,
						   BigDecimal copyFactor, BigDecimal finalScore, boolean injectionFlag, ReviewCause causeSuggestion,
						   String gradingDetailJson) {
		if (!isPending()) {
			throw new IllegalStateException("이미 채점된 답입니다.");
		}
		if (verdict == Verdict.PENDING) {
			throw new IllegalArgumentException("채점 결과가 PENDING 일 수 없습니다.");
		}
		this.verdict = verdict;
		this.judgedBy = judgedBy;
		this.rawScore = rawScore;
		this.copyRatio = copyRatio;
		this.copyFactor = copyFactor;
		this.finalScore = finalScore;
		this.injectionFlag = injectionFlag;
		this.causeSuggestion = causeSuggestion;
		this.gradingDetailJson = gradingDetailJson;
	}

}
