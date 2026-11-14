package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.SolveStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.event.BattleSolvedEvent;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.question.service.DiscipleSolver;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;

class BattleSolveStoreTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long BATTLE_ID = 50L;
	private static final long MINE = 5L;
	private static final long FRIENDS = 6L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleQuestionRepository questionRepository = mock(BattleQuestionRepository.class);
	private final BattleAnswerRepository answerRepository = mock(BattleAnswerRepository.class);
	private final NoteLookupService noteLookupService = mock(NoteLookupService.class);
	private final QuestionBankService questionBankService = mock(QuestionBankService.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final BattleSolveStore store = new BattleSolveStore(battleRepository, participantRepository, questionRepository,
		answerRepository, noteLookupService, questionBankService, eventPublisher);

	private Battle battle;
	private BattleParticipant mine;
	private BattleParticipant friends;

	@BeforeEach
	void 기본_상태() {
		battle = Battle.openRoom(1L, 2L, 2, 10L, "K7M4QX", NOW.plusSeconds(600));
		ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
		mine = participant(MINE, 10L, "[7,8]");
		friends = participant(FRIENDS, 11L, "[9]");
		when(battleRepository.findLockedById(BATTLE_ID)).thenReturn(Optional.of(battle));
		when(participantRepository.findById(MINE)).thenReturn(Optional.of(mine));
		when(participantRepository.findById(FRIENDS)).thenReturn(Optional.of(friends));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
		when(participantRepository.findLockedByBattleId(BATTLE_ID)).thenReturn(List.of(mine, friends));
		BattleQuestion first = battleQuestion(31L, 900L, 1);
		BattleQuestion second = battleQuestion(32L, 901L, 2);
		when(questionRepository.findByBattleIdOrderBySeqAsc(BATTLE_ID)).thenReturn(List.of(first, second));
		when(noteLookupService.findByIds(anyCollection())).thenReturn(List.of(new NoteRef(7L, "스택은 후입선출"), new NoteRef(8L, "큐는 선입선출")));
		when(questionBankService.stems(anyCollection())).thenReturn(Map.of(900L, "스택이란?", 901L, "큐란?"));
	}

	private static BattleParticipant participant(long id, long userId, String noteIdsJson) {
		BattleParticipant participant = BattleParticipant.joined(BATTLE_ID, userId, userId * 10, id == MINE, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		participant.markReady(noteIdsJson, 1, false, NOW);
		return participant;
	}

	private static BattleQuestion battleQuestion(long id, long questionId, int seq) {
		BattleQuestion question = BattleQuestion.of(BATTLE_ID, questionId, seq, null);
		ReflectionTestUtils.setField(question, "id", id);
		return question;
	}

	private static List<DiscipleSolver.SolvedAnswer> answers() {
		return List.of(new DiscipleSolver.SolvedAnswer(900L, "후입선출", false, List.of(7L), false),
			new DiscipleSolver.SolvedAnswer(901L, "", true, List.of(), true));
	}

	// --- begin ---

	@Test
	void 풀이를_시작하면_참가자가_SOLVING이_되고_스냅샷_노트와_문항을_준다() {
		BattleSolveStore.SolveContext context = store.begin(MINE).orElseThrow();

		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.SOLVING);
		assertThat(context.battleId()).isEqualTo(BATTLE_ID);
		assertThat(context.userId()).isEqualTo(10L);
		assertThat(context.notes()).extracting(NoteRef::id).containsExactly(7L, 8L);
		assertThat(context.questions()).extracting(DiscipleSolver.SolveQuestion::id).containsExactly(900L, 901L);
		assertThat(context.questions()).extracting(DiscipleSolver.SolveQuestion::stem).containsExactly("스택이란?", "큐란?");
		verify(noteLookupService).findByIds(List.of(7L, 8L));   // 지금의 노트가 아니라 스냅샷한 id 로 읽는다
	}

	@Test
	void 같은_풀이를_두_번_시작할_수_없다() {
		assertThat(store.begin(MINE)).isPresent();

		assertThat(store.begin(MINE)).isEmpty();
	}

	@Test
	void RUNNING이_아닌_배틀의_풀이는_시작하지_않는다() {
		for (BattleStatus status : List.of(BattleStatus.CANCELED, BattleStatus.ERROR, BattleStatus.FINISHED)) {
			ReflectionTestUtils.setField(battle, "status", status);

			assertThat(store.begin(MINE)).isEmpty();
		}
		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		verifyNoInteractions(noteLookupService);
	}

	@Test
	void 없는_참가자는_빈_값이다() {
		when(participantRepository.findById(99L)).thenReturn(Optional.empty());

		assertThat(store.begin(99L)).isEmpty();
	}

	// --- complete ---

	@Test
	void 한_명만_풀었으면_답을_PENDING으로_저장하고_알리지_않는다() {
		mine.claimSolve();

		store.complete(MINE, answers(), 2);

		ArgumentCaptor<BattleAnswer> saved = ArgumentCaptor.forClass(BattleAnswer.class);
		verify(answerRepository, org.mockito.Mockito.times(2)).save(saved.capture());
		BattleAnswer first = saved.getAllValues().get(0);
		assertThat(first.getParticipantId()).isEqualTo(MINE);
		assertThat(first.getBattleQuestionId()).isEqualTo(31L);   // 문항 id 가 아니라 이 배틀의 battle_question id
		assertThat(first.getAnswerText()).isEqualTo("후입선출");
		assertThat(first.getCitedNoteIdsJson()).isEqualTo("[7]");
		assertThat(first.getVerdict()).isEqualTo(Verdict.PENDING);
		BattleAnswer second = saved.getAllValues().get(1);
		assertThat(second.getBattleQuestionId()).isEqualTo(32L);
		assertThat(second.isUnknown()).isTrue();
		assertThat(second.getCitedNoteIdsJson()).isEqualTo("[]");

		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
		assertThat(mine.getSolveAttempts()).isEqualTo(2);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 둘_다_풀었으면_채점_이벤트를_낸다() {
		friends.claimSolve();
		friends.markSolved(1);
		mine.claimSolve();

		store.complete(MINE, answers(), 1);

		verify(eventPublisher).publishEvent(new BattleSolvedEvent(BATTLE_ID));
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
	}

	@Test
	void 둘_다_풀었는지는_일반_읽기가_아니라_잠금_읽기로_본다() {
		// 일반 읽기(스냅샷)는 상대가 방금 커밋한 풀이 완료를 못 보고 SOLVING 으로 보여 준다 — 낡은 값이다.
		BattleParticipant staleFriends = participant(FRIENDS, 11L, "[9]");
		staleFriends.claimSolve();
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, staleFriends));
		// 잠금 읽기는 최신 값을 본다: 상대는 이미 SOLVED
		friends.claimSolve();
		friends.markSolved(1);
		mine.claimSolve();

		store.complete(MINE, answers(), 1);

		verify(eventPublisher).publishEvent(new BattleSolvedEvent(BATTLE_ID));
		verify(participantRepository).findLockedByBattleId(BATTLE_ID);
	}

	@Test
	void ERROR인_배틀에서_늦게_끝난_풀이는_저장하되_알리지_않는다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.ERROR);
		friends.claimSolve();
		friends.markSolved(1);
		mine.claimSolve();

		store.complete(MINE, answers(), 1);

		verify(answerRepository, org.mockito.Mockito.times(2)).save(any());
		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 풀이_도중_취소된_배틀에는_아무것도_저장하지_않는다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.CANCELED);
		mine.claimSolve();

		store.complete(MINE, answers(), 1);

		verify(answerRepository, never()).save(any());
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 풀고_있지_않은_참가자의_결과는_받지_않는다() {
		store.complete(MINE, answers(), 1);   // 아직 PENDING

		verify(answerRepository, never()).save(any());
	}

	@Test
	void 이_배틀에_없는_문항의_답은_버린다() {
		mine.claimSolve();

		store.complete(MINE, List.of(new DiscipleSolver.SolvedAnswer(777L, "엉뚱", false, List.of(7L), false)), 1);

		verify(answerRepository, never()).save(any());
	}

	// --- fail ---

	@Test
	void 풀이가_실패하면_배틀이_ERROR가_되고_이유가_남는다() {
		mine.claimSolve();

		store.fail(MINE, "AI_UNAVAILABLE", 3);

		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.FAILED);
		assertThat(mine.getSolveAttempts()).isEqualTo(3);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.ERROR);
		assertThat(battle.getErrorReason()).isEqualTo("AI_UNAVAILABLE");
	}

	@Test
	void 접수하지_못한_참가자도_실패로_닫을_수_있다() {
		store.fail(MINE, "QUEUE_FULL", 0);   // 아직 PENDING

		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.FAILED);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.ERROR);
	}

	@Test
	void 이미_푼_참가자는_실패로_바꾸지_않는다() {
		mine.claimSolve();
		mine.markSolved(1);

		store.fail(MINE, "LATE_ERROR", 1);

		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
	}

	@Test
	void 이미_ERROR인_배틀에서_다른_참가자가_실패해도_배틀은_ERROR_그대로다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.ERROR);
		battle.retrySolve();
		battle.markError("FIRST");
		friends.claimSolve();

		store.fail(FRIENDS, "SECOND", 2);

		assertThat(friends.getSolveStatus()).isEqualTo(SolveStatus.FAILED);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.ERROR);
		assertThat(battle.getErrorReason()).isEqualTo("FIRST");
	}

	// --- recover ---

	@Test
	void 재시작_복구는_풀던_참가자를_다시_접수하고_이미_푼_참가자는_둔다() {
		mine.claimSolve();   // 풀던 중에 서버가 꺼졌다
		friends.claimSolve();
		friends.markSolved(1);

		BattleSolveStore.Recovery recovery = store.recover(BATTLE_ID);

		assertThat(recovery.participantIdsToSolve()).containsExactly(MINE);
		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.PENDING);
		assertThat(friends.getSolveStatus()).isEqualTo(SolveStatus.SOLVED);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 모두_풀었는데_채점_전이었다면_채점_이벤트를_다시_낸다() {
		for (BattleParticipant participant : List.of(mine, friends)) {
			participant.claimSolve();
			participant.markSolved(1);
		}

		BattleSolveStore.Recovery recovery = store.recover(BATTLE_ID);

		assertThat(recovery.participantIdsToSolve()).isEmpty();
		verify(eventPublisher).publishEvent(new BattleSolvedEvent(BATTLE_ID));
	}

	@Test
	void RUNNING이_아닌_배틀은_복구하지_않는다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.ERROR);
		mine.claimSolve();

		assertThat(store.recover(BATTLE_ID).participantIdsToSolve()).isEmpty();
		assertThat(mine.getSolveStatus()).isEqualTo(SolveStatus.SOLVING);
		verifyNoInteractions(eventPublisher);
	}

}
