package com.aidisciple.domain.battle.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.ParticipantStatus;
import com.aidisciple.domain.battle.entity.SolveStatus;

/**
 * B-02 입장, B-03 방·진행 상태(폴링). 진행 중에는 문항·답·상대 노트를 일절 담지 않는다.
 * 연습 배틀은 {@code roomCode}·{@code expiresAt} 가 null 이고 {@code participants} 에 나만 있다.
 */
public record BattleResponse(
	Long id,
	BattleStatus status,
	BattleMode mode,
	Course course,
	Unit unit,
	int questionCount,
	String roomCode,
	Instant expiresAt,
	List<Participant> participants,
	Summary summary
) {

	public record Course(Long id, String name) {
	}

	public record Unit(Long id, String title) {
	}

	public record Participant(
		Long userId,
		String nickname,
		boolean isMe,
		ParticipantStatus status,
		SolveStatus solveStatus
	) {
	}

	/** FINISHED 일 때만 있다. 연습 배틀은 {@code opponentScore}·{@code result}·{@code eloDelta} 가 null 이다. */
	public record Summary(BigDecimal myScore, BigDecimal opponentScore, BattleResult result, Integer eloDelta) {
	}

}
