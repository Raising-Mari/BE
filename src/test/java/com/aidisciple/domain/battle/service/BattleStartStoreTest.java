package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.SolveStatus;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class BattleStartStoreTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long BATTLE_ID = 50L;
	private static final long ME = 10L;
	private static final long FRIEND = 11L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleQuestionRepository questionRepository = mock(BattleQuestionRepository.class);
	private final NoteLookupService noteLookupService = mock(NoteLookupService.class);
	private final QuestionBankService questionBankService = mock(QuestionBankService.class);
	private final UnitService unitService = mock(UnitService.class);
	private final BattleStartStore store = new BattleStartStore(battleRepository, participantRepository, questionRepository,
		noteLookupService, questionBankService, unitService, Clock.fixed(NOW, ZoneOffset.UTC));

	private Battle battle;
	private BattleParticipant mine;
	private BattleParticipant friends;

	@BeforeEach
	void 기본_상태() {
		battle = Battle.openRoom(1L, 2L, 3, ME, "K7M4QX", NOW.plus(Duration.ofMinutes(20)));
		ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
		ReflectionTestUtils.setField(battle, "status", BattleStatus.LOBBY);
		mine = participant(1L, ME, 100L, true);
		friends = participant(2L, FRIEND, 101L, false);
		when(battleRepository.findLockedById(BATTLE_ID)).thenReturn(Optional.of(battle));
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, ME)).thenReturn(Optional.of(mine));
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, FRIEND)).thenReturn(Optional.of(friends));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
		when(noteLookupService.activeNotes(100L, 2L)).thenReturn(List.of(new NoteRef(7L, "a"), new NoteRef(8L, "b")));
		when(noteLookupService.activeNotes(101L, 2L)).thenReturn(List.of(new NoteRef(9L, "c")));
		when(unitService.findRefs(List.of(2L))).thenReturn(Map.of(2L, new UnitRef(2L, 1L, "3주차")));
		when(questionBankService.pickForBattle(2L, List.of(ME, FRIEND), 3, BATTLE_ID)).thenReturn(List.of(900L, 901L, 902L));
	}

	private static BattleParticipant participant(long id, long userId, long discipleId, boolean creator) {
		BattleParticipant participant = BattleParticipant.joined(BATTLE_ID, userId, discipleId, creator, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		return participant;
	}

	private static ErrorCode codeOf(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode();
	}

	// --- B-04 ---

	@Test
	void 먼저_준비하면_그_순간의_노트를_스냅샷하고_상대를_기다린다() {
		BattleStartStore.ReadyOutcome outcome = store.markReady(ME, BATTLE_ID, true);

		assertThat(outcome.result()).isEqualTo(BattleStartStore.ReadyResult.WAITING_FOR_OTHER);
		assertThat(mine.isReady()).isTrue();
		assertThat(mine.getNoteIdsJson()).isEqualTo("[7,8]");
		assertThat(mine.getNoteCountSnapshot()).isEqualTo(2);
		assertThat(mine.getReadyAt()).isEqualTo(NOW);
		assertThat(mine.isNotesPublic()).isTrue();
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.LOBBY);
		verify(questionBankService, never()).pickForBattle(anyLong(), any(), anyInt(), anyLong());
	}

	@Test
	void 둘이_모두_준비하면_문항을_고정하고_본_문항을_기록하고_RUNNING이_된다() {
		store.markReady(ME, BATTLE_ID, null);

		BattleStartStore.ReadyOutcome outcome = store.markReady(FRIEND, BATTLE_ID, null);

		assertThat(outcome.result()).isEqualTo(BattleStartStore.ReadyResult.STARTED);
		assertThat(outcome.participantIds()).containsExactly(1L, 2L);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(battle.getStartedAt()).isEqualTo(NOW);
		assertThat(friends.getNoteIdsJson()).isEqualTo("[9]");

		ArgumentCaptor<BattleQuestion> saved = ArgumentCaptor.forClass(BattleQuestion.class);
		verify(questionRepository, times(3)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(BattleQuestion::getQuestionId).containsExactly(900L, 901L, 902L);
		assertThat(saved.getAllValues()).extracting(BattleQuestion::getSeq).containsExactly(1, 2, 3);
		assertThat(saved.getAllValues()).extracting(BattleQuestion::getSourceAnswerId).containsOnlyNulls();
		verify(questionBankService).recordSeen(List.of(ME, FRIEND), List.of(900L, 901L, 902L), BATTLE_ID);
	}

	@Test
	void 이미_준비한_사람이_다시_눌러도_스냅샷은_그대로이고_노트를_다시_읽지_않는다() {
		store.markReady(ME, BATTLE_ID, false);
		when(noteLookupService.activeNotes(100L, 2L)).thenReturn(List.of(new NoteRef(7L, "a"), new NoteRef(8L, "b"), new NoteRef(10L, "d")));

		BattleStartStore.ReadyOutcome again = store.markReady(ME, BATTLE_ID, true);

		assertThat(again.result()).isEqualTo(BattleStartStore.ReadyResult.ALREADY_READY);
		assertThat(mine.getNoteIdsJson()).isEqualTo("[7,8]");
		assertThat(mine.isNotesPublic()).isFalse();
		verify(noteLookupService, times(1)).activeNotes(100L, 2L);
	}

	@Test
	void 시작된_뒤에_먼저_누른_사람이_다시_눌러도_성공이다() {
		store.markReady(ME, BATTLE_ID, null);
		store.markReady(FRIEND, BATTLE_ID, null);

		assertThat(store.markReady(ME, BATTLE_ID, null).result()).isEqualTo(BattleStartStore.ReadyResult.ALREADY_READY);
		verify(questionRepository, times(3)).save(any());   // 문항은 한 번만 고정된다
	}

	@Test
	void 둘_다_안_본_문항이_모자라면_시작하지_않고_두_사람의_준비와_스냅샷이_풀린다() {
		when(questionBankService.pickForBattle(2L, List.of(ME, FRIEND), 3, BATTLE_ID)).thenReturn(List.of());
		store.markReady(ME, BATTLE_ID, null);

		BattleStartStore.ReadyOutcome outcome = store.markReady(FRIEND, BATTLE_ID, null);

		assertThat(outcome.result()).isEqualTo(BattleStartStore.ReadyResult.NOT_ENOUGH_QUESTIONS);
		assertThat(outcome.unitId()).isEqualTo(2L);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.LOBBY);
		assertThat(List.of(mine, friends)).allSatisfy(participant -> {
			assertThat(participant.isReady()).isFalse();
			assertThat(participant.getNoteIdsJson()).isNull();
			assertThat(participant.getNoteCountSnapshot()).isZero();
		});
		verify(questionRepository, never()).save(any());
		verify(questionBankService, never()).recordSeen(any(), any(), anyLong());
	}

	@Test
	void 상대가_오기_전에는_준비할_수_없다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.WAITING);

		assertThat(codeOf(() -> store.markReady(ME, BATTLE_ID, null))).isEqualTo(ErrorCode.CONFLICT);
		assertThat(mine.isReady()).isFalse();
	}

	@Test
	void 취소된_방에서는_준비할_수_없다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.CANCELED);
		assertThat(codeOf(() -> store.markReady(ME, BATTLE_ID, null))).isEqualTo(ErrorCode.CONFLICT);

		mine.markReady("[7]", 1, false, NOW);   // 이미 준비한 사람도 취소된 방이면 거부
		assertThat(codeOf(() -> store.markReady(ME, BATTLE_ID, null))).isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	void 노트가_0개면_준비할_수_없고_주차를_알려_준다() {
		when(noteLookupService.activeNotes(100L, 2L)).thenReturn(List.of());

		Throwable thrown = catchThrowable(() -> store.markReady(ME, BATTLE_ID, null));

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BATTLE_NOTES_REQUIRED);
			assertThat(e.getDetails()).containsEntry("courseId", 1L);
		});
		assertThat(mine.isReady()).isFalse();
	}

	@Test
	void 입장_시간이_지난_방은_준비할_수_없다() {
		ReflectionTestUtils.setField(battle, "expiresAt", NOW.minusSeconds(1));

		assertThat(codeOf(() -> store.markReady(ME, BATTLE_ID, null))).isEqualTo(ErrorCode.BATTLE_ROOM_EXPIRED);
	}

	@Test
	void 참가자가_아니거나_없는_배틀이면_NOT_FOUND다() {
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, 99L)).thenReturn(Optional.empty());
		when(battleRepository.findLockedById(404L)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> store.markReady(99L, BATTLE_ID, null))).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(codeOf(() -> store.markReady(ME, 404L, null))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	// --- B-09 ---

	@Test
	void 재시도하면_RUNNING으로_돌아가고_실패한_참가자만_다시_접수한다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.ERROR);
		mine.claimSolve();
		mine.markSolveFailed(3);
		friends.claimSolve();
		friends.markSolved(1);

		List<Long> toSolve = store.retrySolve(FRIEND, BATTLE_ID);   // 참가자 누구나

		assertThat(toSolve).containsExactly(1L);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		assertThat(friends.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
	}

	@Test
	void ERROR가_아니면_재시도할_수_없다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);

		assertThat(codeOf(() -> store.retrySolve(ME, BATTLE_ID))).isEqualTo(ErrorCode.CONFLICT);
	}

	@Test
	void 참가자가_아니면_재시도할_수_없다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.ERROR);
		when(participantRepository.findByBattleIdAndUserId(BATTLE_ID, 99L)).thenReturn(Optional.empty());

		assertThat(codeOf(() -> store.retrySolve(99L, BATTLE_ID))).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.ERROR);
	}

}
