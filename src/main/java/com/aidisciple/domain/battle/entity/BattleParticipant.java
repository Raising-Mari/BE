package com.aidisciple.domain.battle.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
 * 배틀 참가자. 준비 완료를 누른 순간 그 사람의 ACTIVE 노트 id 를 {@code noteIdsJson} 에 스냅샷하고(연습 배틀은 시작 순간),
 * 이미 READY 이면 덮어쓰지 않는다. 풀이는 항상 이 스냅샷의 노트로 하므로 그 뒤에 노트를 고쳐도 이 판의 풀이는 바뀌지 않는다.
 * 점수·건수는 채점(WP9)이 채운다.
 */
@Getter
@Entity
@Table(name = "battle_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BattleParticipant extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long battleId;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long discipleId;

	@Column(name = "is_creator", nullable = false)
	private boolean creator;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ParticipantStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private SolveStatus solveStatus;

	@Column(nullable = false, columnDefinition = "TINYINT")
	private int solveAttempts;

	@Column(columnDefinition = "JSON")
	private String noteIdsJson;

	@Column(nullable = false)
	private int noteCountSnapshot;

	@Column(nullable = false)
	private boolean notesPublic;

	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal totalScore = BigDecimal.ZERO;

	@Column(nullable = false)
	private int correctCount;

	@Column(nullable = false)
	private int partialCount;

	@Column(nullable = false)
	private int wrongCount;

	@Column(nullable = false)
	private int unknownCount;

	@Column(nullable = false)
	private int flaggedCount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 12)
	private BattleResult result;

	private Instant joinedAt;

	private Instant readyAt;

	private Instant answersRevealedAt;

	/** 방을 만들었거나 방에 입장한 직후의 참가자 */
	public static BattleParticipant joined(Long battleId, Long userId, Long discipleId, boolean creator, Instant now) {
		BattleParticipant participant = new BattleParticipant();
		participant.battleId = battleId;
		participant.userId = userId;
		participant.discipleId = discipleId;
		participant.creator = creator;
		participant.status = ParticipantStatus.JOINED;
		participant.solveStatus = SolveStatus.PENDING;
		participant.joinedAt = now;
		return participant;
	}

	/** 연습 배틀의 참가자. 대기실이 없어 만들 때 이미 READY 이고 노트 스냅샷도 이 순간 찍는다(0개여도 된다). */
	public static BattleParticipant practice(Long battleId, Long userId, Long discipleId, String noteIdsJson,
											 int noteCount, Instant now) {
		BattleParticipant participant = joined(battleId, userId, discipleId, true, now);
		participant.status = ParticipantStatus.READY;
		participant.noteIdsJson = noteIdsJson;
		participant.noteCountSnapshot = noteCount;
		participant.readyAt = now;
		return participant;
	}

	public boolean isReady() {
		return status == ParticipantStatus.READY;
	}

	/**
	 * 준비 완료. 이미 READY 이면 아무것도 하지 않고 false — 먼저 누른 순간의 노트와 공개 여부가 유지된다(멱등).
	 *
	 * @param notesPublic 본문을 생략했으면 null: 기존 값(기본 비공개)을 그대로 둔다
	 */
	public boolean markReady(String noteIdsJson, int noteCount, Boolean notesPublic, Instant now) {
		if (isReady()) {
			return false;
		}
		this.status = ParticipantStatus.READY;
		this.noteIdsJson = noteIdsJson;
		this.noteCountSnapshot = noteCount;
		this.readyAt = now;
		if (notesPublic != null) {
			this.notesPublic = notesPublic;
		}
		return true;
	}

	/** 문항이 모자라 시작하지 못했을 때: 준비와 스냅샷을 비워 다시 누를 때 새로 찍게 한다. 공개 여부는 그대로 둔다. */
	public void resetReady() {
		this.status = ParticipantStatus.JOINED;
		this.noteIdsJson = null;
		this.noteCountSnapshot = 0;
		this.readyAt = null;
	}

	public void changeNotesPublic(boolean notesPublic) {
		this.notesPublic = notesPublic;
	}

	/** 채점이 끝나 점수·건수·승패를 채운다(WP9). 연습 배틀은 {@code result} 가 null 이다. */
	public void applyResult(BigDecimal totalScore, int correct, int partial, int wrong, int unknown, int flagged,
							BattleResult result) {
		this.totalScore = totalScore;
		this.correctCount = correct;
		this.partialCount = partial;
		this.wrongCount = wrong;
		this.unknownCount = unknown;
		this.flaggedCount = flagged;
		this.result = result;
	}

	/** 풀이 시작을 선점한다. 이미 풀고 있거나 끝났으면 false — 같은 작업이 두 번 접수돼도 한 번만 푼다. */
	public boolean claimSolve() {
		if (solveStatus != SolveStatus.PENDING) {
			return false;
		}
		solveStatus = SolveStatus.SOLVING;
		return true;
	}

	public void markSolved(int attempts) {
		solveStatus = SolveStatus.SOLVED;
		solveAttempts = attempts;
	}

	public void markSolveFailed(int attempts) {
		solveStatus = SolveStatus.FAILED;
		solveAttempts = attempts;
	}

	/** B-09: 실패한 풀이를 다시 접수 상태로 되돌린다. 이미 푼 참가자는 건드리지 않는다. */
	public boolean resetSolveForRetry() {
		if (solveStatus != SolveStatus.FAILED) {
			return false;
		}
		solveStatus = SolveStatus.PENDING;
		solveAttempts = 0;
		return true;
	}

	/** 서버 재시작 복구: 풀던 중이던 것을 다시 접수 상태로 되돌린다. */
	public boolean releaseSolving() {
		if (solveStatus != SolveStatus.SOLVING) {
			return false;
		}
		solveStatus = SolveStatus.PENDING;
		return true;
	}

	public boolean isAnswersRevealed() {
		return answersRevealedAt != null;
	}

	/** R-11 정답 보기. 되돌릴 수 없고, 이미 열었으면 처음 연 시각을 그대로 돌려준다(멱등). 내 행에만 기록한다. */
	public Instant revealAnswers(Instant now) {
		if (answersRevealedAt == null) {
			answersRevealedAt = now;
		}
		return answersRevealedAt;
	}


}
