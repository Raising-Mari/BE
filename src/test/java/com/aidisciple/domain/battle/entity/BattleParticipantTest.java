package com.aidisciple.domain.battle.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class BattleParticipantTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

	private static BattleParticipant joined() {
		return BattleParticipant.joined(1L, 10L, 100L, false, NOW);
	}

	@Test
	void 입장한_참가자는_JOINED_PENDING이다() {
		BattleParticipant participant = joined();

		assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.JOINED);
		assertThat(participant.getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		assertThat(participant.isReady()).isFalse();
		assertThat(participant.isNotesPublic()).isFalse();
		assertThat(participant.getNoteIdsJson()).isNull();
	}

	@Test
	void 준비하면_그_순간의_노트와_시각이_남는다() {
		BattleParticipant participant = joined();

		boolean first = participant.markReady("[3,5]", 2, true, NOW.plusSeconds(5));

		assertThat(first).isTrue();
		assertThat(participant.isReady()).isTrue();
		assertThat(participant.getNoteIdsJson()).isEqualTo("[3,5]");
		assertThat(participant.getNoteCountSnapshot()).isEqualTo(2);
		assertThat(participant.getReadyAt()).isEqualTo(NOW.plusSeconds(5));
		assertThat(participant.isNotesPublic()).isTrue();
	}

	@Test
	void 이미_준비했으면_스냅샷을_덮어쓰지_않는다() {
		BattleParticipant participant = joined();
		participant.markReady("[3,5]", 2, false, NOW);

		boolean again = participant.markReady("[3,5,9]", 3, true, NOW.plusSeconds(60));

		assertThat(again).isFalse();
		assertThat(participant.getNoteIdsJson()).isEqualTo("[3,5]");
		assertThat(participant.getNoteCountSnapshot()).isEqualTo(2);
		assertThat(participant.getReadyAt()).isEqualTo(NOW);
		assertThat(participant.isNotesPublic()).isFalse();
	}

	@Test
	void 공개_여부를_생략하면_기존_값을_그대로_둔다() {
		BattleParticipant participant = joined();
		participant.changeNotesPublic(true);

		participant.markReady("[1]", 1, null, NOW);

		assertThat(participant.isNotesPublic()).isTrue();
	}

	@Test
	void 준비를_풀면_스냅샷이_비워지고_공개_여부는_남는다() {
		BattleParticipant participant = joined();
		participant.markReady("[3,5]", 2, true, NOW);

		participant.resetReady();

		assertThat(participant.isReady()).isFalse();
		assertThat(participant.getNoteIdsJson()).isNull();
		assertThat(participant.getNoteCountSnapshot()).isZero();
		assertThat(participant.getReadyAt()).isNull();
		assertThat(participant.isNotesPublic()).isTrue();
		// 다시 누르면 새로 찍힌다
		assertThat(participant.markReady("[3,5,9]", 3, null, NOW.plusSeconds(30))).isTrue();
		assertThat(participant.getNoteIdsJson()).isEqualTo("[3,5,9]");
	}

	@Test
	void 풀이는_PENDING에서_한_번만_선점된다() {
		BattleParticipant participant = joined();

		assertThat(participant.claimSolve()).isTrue();
		assertThat(participant.getSolveStatus()).isEqualTo(SolveStatus.SOLVING);
		assertThat(participant.claimSolve()).isFalse();
	}

	@Test
	void 풀이가_끝나면_시도_횟수가_남는다() {
		BattleParticipant solved = joined();
		solved.claimSolve();
		solved.markSolved(2);

		assertThat(solved.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
		assertThat(solved.getSolveAttempts()).isEqualTo(2);
		assertThat(solved.claimSolve()).isFalse();   // 푼 참가자는 다시 풀지 않는다

		BattleParticipant failed = joined();
		failed.claimSolve();
		failed.markSolveFailed(3);
		assertThat(failed.getSolveStatus()).isEqualTo(SolveStatus.FAILED);
		assertThat(failed.getSolveAttempts()).isEqualTo(3);
	}

	@Test
	void 재시도는_실패한_풀이만_접수_상태로_되돌린다() {
		BattleParticipant failed = joined();
		failed.claimSolve();
		failed.markSolveFailed(3);
		BattleParticipant solved = joined();
		solved.claimSolve();
		solved.markSolved(1);

		assertThat(failed.resetSolveForRetry()).isTrue();
		assertThat(failed.getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		assertThat(failed.getSolveAttempts()).isZero();
		assertThat(solved.resetSolveForRetry()).isFalse();
		assertThat(solved.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
	}

	@Test
	void 재시작_복구는_풀던_중인_것만_접수_상태로_되돌린다() {
		BattleParticipant solving = joined();
		solving.claimSolve();

		assertThat(solving.releaseSolving()).isTrue();
		assertThat(solving.getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		assertThat(joined().releaseSolving()).isFalse();
	}

	@Test
	void 연습_참가자는_만들_때_이미_준비돼_있다() {
		BattleParticipant participant = BattleParticipant.practice(2L, 10L, 100L, "[]", 0, NOW);

		assertThat(participant.isReady()).isTrue();
		assertThat(participant.isCreator()).isTrue();
		assertThat(participant.getNoteIdsJson()).isEqualTo("[]");
		assertThat(participant.getNoteCountSnapshot()).isZero();
		assertThat(participant.getReadyAt()).isEqualTo(NOW);
	}

	@Test
	void 채점_전의_답은_PENDING_0점이다() {
		BattleAnswer answer = BattleAnswer.solved(1L, 2L, "후입선출", false, "[3]");

		assertThat(answer.getVerdict()).isEqualTo(Verdict.PENDING);
		assertThat(answer.getJudgedBy()).isEqualTo(JudgedBy.CODE);
		assertThat(answer.getFinalScore()).isEqualByComparingTo("0");
		assertThat(answer.getAnswerText()).isEqualTo("후입선출");

		BattleAnswer unknown = BattleAnswer.solved(1L, 3L, "무시되는 글", true, "[]");
		assertThat(unknown.isUnknown()).isTrue();
		assertThat(unknown.getAnswerText()).isNull();
	}

}
