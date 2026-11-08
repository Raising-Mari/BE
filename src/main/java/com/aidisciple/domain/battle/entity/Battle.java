package com.aidisciple.domain.battle.entity;

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

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 배틀 한 판. 상태 전이는 이 클래스의 메서드로만 하고, 허용되지 않은 전이는 {@code CONFLICT} 다.
 * <pre>
 * WAITING → LOBBY → RUNNING → FINISHED
 * WAITING/LOBBY → CANCELED/EXPIRED      RUNNING → ERROR      ERROR → RUNNING(재시도) / CANCELED(포기)
 * </pre>
 * {@code activeRoomCode} 는 WAITING·LOBBY 일 때만 값이 있어 진행 중인 방끼리만 코드가 겹치지 않게 한다.
 * 방을 떠나는 모든 전이가 이 값을 비운다. {@code roomCode} 는 남겨 둬서 만료된 코드를 알려 줄 수 있게 한다.
 */
@Getter
@Entity
@Table(name = "battles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Battle extends BaseTimeEntity {

	private static final int ERROR_REASON_MAX = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private Long unitId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private BattleMode mode;

	private Long sourceBattleId;

	@Column(length = 6, columnDefinition = "CHAR(6)")
	private String roomCode;

	@Column(length = 6, columnDefinition = "CHAR(6)")
	private String activeRoomCode;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private BattleStatus status;

	@Column(nullable = false, columnDefinition = "TINYINT")
	private int questionCount;

	@Column(nullable = false)
	private Long createdBy;

	private Instant expiresAt;

	private Instant startedAt;

	private Instant finishedAt;

	@Column(length = ERROR_REASON_MAX)
	private String errorReason;

	/** B-01. 방을 만든 사람 혼자 있는 WAITING 방 */
	public static Battle openRoom(Long courseId, Long unitId, int questionCount, Long createdBy, String roomCode,
								  Instant expiresAt) {
		Battle battle = new Battle();
		battle.courseId = courseId;
		battle.unitId = unitId;
		battle.mode = BattleMode.RANKED;
		battle.roomCode = roomCode;
		battle.activeRoomCode = roomCode;
		battle.status = BattleStatus.WAITING;
		battle.questionCount = questionCount;
		battle.createdBy = createdBy;
		battle.expiresAt = expiresAt;
		return battle;
	}

	/** B-10. 대기실이 없어 방 코드 없이 곧바로 RUNNING 으로 시작한다. */
	public static Battle startPractice(Long courseId, Long unitId, Long sourceBattleId, int questionCount,
									   Long createdBy, Instant now) {
		Battle battle = new Battle();
		battle.courseId = courseId;
		battle.unitId = unitId;
		battle.mode = BattleMode.PRACTICE;
		battle.sourceBattleId = sourceBattleId;
		battle.status = BattleStatus.RUNNING;
		battle.questionCount = questionCount;
		battle.createdBy = createdBy;
		battle.startedAt = now;
		return battle;
	}

	/** 입장 시간이 지났는가. 만료 작업(J-04)이 돌기 전이라도 지난 방은 지난 것으로 본다. */
	public boolean isExpiredAt(Instant now) {
		return status.isOpenRoom() && expiresAt != null && !expiresAt.isAfter(now);
	}

	public boolean isPractice() {
		return mode == BattleMode.PRACTICE;
	}

	/** 상대가 들어왔다 */
	public void markLobby() {
		require(status == BattleStatus.WAITING);
		status = BattleStatus.LOBBY;
	}

	/** 두 사람이 모두 준비해 문항이 고정됐다 */
	public void start(Instant now) {
		require(status == BattleStatus.LOBBY);
		status = BattleStatus.RUNNING;
		startedAt = now;
		releaseRoomCode();
	}

	/** 방 취소·나가기, ERROR 에서는 포기. RUNNING·FINISHED 는 취소할 수 없다. */
	public void cancel() {
		require(status == BattleStatus.WAITING || status == BattleStatus.LOBBY || status == BattleStatus.ERROR);
		status = BattleStatus.CANCELED;
		releaseRoomCode();
	}

	/** J-04 */
	public void expire() {
		require(status.isOpenRoom());
		status = BattleStatus.EXPIRED;
		releaseRoomCode();
	}

	/** 제자 풀이가 끝내 실패했다. Elo 는 바뀌지 않는다. */
	public void markError(String reason) {
		require(status == BattleStatus.RUNNING);
		status = BattleStatus.ERROR;
		errorReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), ERROR_REASON_MAX));
	}

	/** B-09: ERROR 에서 풀이를 다시 시도한다. */
	public void retrySolve() {
		require(status == BattleStatus.ERROR);
		status = BattleStatus.RUNNING;
		errorReason = null;
	}

	/** 채점이 끝났다(WP9). */
	public void finish(Instant now) {
		require(status == BattleStatus.RUNNING);
		status = BattleStatus.FINISHED;
		finishedAt = now;
	}

	private void releaseRoomCode() {
		activeRoomCode = null;
	}

	private static void require(boolean allowed) {
		if (!allowed) {
			throw new BusinessException(ErrorCode.CONFLICT);
		}
	}

}
