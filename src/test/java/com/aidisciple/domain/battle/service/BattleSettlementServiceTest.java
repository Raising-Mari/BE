package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.JudgedBy;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.event.BattleFinishedEvent;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.review.entity.ReviewCause;

class BattleSettlementServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long BATTLE_ID = 50L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleQuestionRepository questionRepository = mock(BattleQuestionRepository.class);
	private final BattleAnswerRepository answerRepository = mock(BattleAnswerRepository.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final BattleSettlementService service = new BattleSettlementService(battleRepository, participantRepository,
		questionRepository, answerRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

	private Battle battle;
	private BattleParticipant mine;
	private BattleParticipant friends;
	private List<BattleAnswer> myAnswers;
	private List<BattleAnswer> friendAnswers;

	@BeforeEach
	void 기본_상태() {
		battle = Battle.openRoom(1L, 2L, 3, 10L, "K7M4QX", NOW);
		ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);
		mine = solvedParticipant(5L, 10L);
		friends = solvedParticipant(6L, 11L);
		// 문항 3개: battle_question id 201~203, question id 901~903
		List<BattleQuestion> questions = List.of(question(201L, 901L, 1), question(202L, 902L, 2), question(203L, 903L, 3));
		myAnswers = answers(5L, 100L);
		friendAnswers = answers(6L, 200L);
		when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
		when(battleRepository.findLockedById(BATTLE_ID)).thenReturn(Optional.of(battle));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
		when(questionRepository.findByBattleIdOrderBySeqAsc(BATTLE_ID)).thenReturn(questions);
		List<BattleAnswer> all = new ArrayList<>(myAnswers);
		all.addAll(friendAnswers);
		when(answerRepository.findByParticipantIdIn(anyCollection())).thenReturn(all);
	}

	private static BattleParticipant solvedParticipant(long id, long userId) {
		BattleParticipant participant = BattleParticipant.joined(BATTLE_ID, userId, userId * 10, id == 5L, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		participant.markReady("[7,8]", 2, false, NOW);
		participant.claimSolve();
		participant.markSolved(1);
		return participant;
	}

	private static BattleQuestion question(long id, long questionId, int seq) {
		BattleQuestion question = BattleQuestion.of(BATTLE_ID, questionId, seq, null);
		ReflectionTestUtils.setField(question, "id", id);
		return question;
	}

	private static List<BattleAnswer> answers(long participantId, long firstAnswerId) {
		List<BattleAnswer> answers = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BattleAnswer answer = BattleAnswer.solved(participantId, 201L + i, "답 " + i, false, "[7]");
			ReflectionTestUtils.setField(answer, "id", firstAnswerId + i);
			answers.add(answer);
		}
		return answers;
	}

	private static BattleSettlementService.AnswerGrade grade(BattleAnswer answer, Verdict verdict, String finalScore) {
		return new BattleSettlementService.AnswerGrade(answer.getId(), verdict, JudgedBy.CODE, new BigDecimal(finalScore),
			new BigDecimal("0.000"), new BigDecimal("1.00"), new BigDecimal(finalScore), verdict == Verdict.ZEROED_INJECTION,
			verdict == Verdict.WRONG ? ReviewCause.NOT_TAUGHT : null, "{}");
	}

	private List<BattleSettlementService.AnswerGrade> grades(List<BattleAnswer> answers, Verdict first, String firstScore,
															Verdict second, String secondScore, Verdict third, String thirdScore) {
		return List.of(grade(answers.get(0), first, firstScore), grade(answers.get(1), second, secondScore),
			grade(answers.get(2), third, thirdScore));
	}

	private List<BattleSettlementService.AnswerGrade> all(List<BattleSettlementService.AnswerGrade> mineGrades,
														  List<BattleSettlementService.AnswerGrade> friendGrades) {
		List<BattleSettlementService.AnswerGrade> all = new ArrayList<>(mineGrades);
		all.addAll(friendGrades);
		return all;
	}

	// --- loadForGrading ---

	@Test
	void 둘_다_풀었으면_채점_전_답과_스냅샷_노트를_문항_순서로_준다() {
		BattleSettlementService.GradingBundle bundle = service.loadForGrading(BATTLE_ID).orElseThrow();

		assertThat(bundle.battleId()).isEqualTo(BATTLE_ID);
		assertThat(bundle.unitId()).isEqualTo(2L);
		assertThat(bundle.mode()).isEqualTo(BattleMode.RANKED);
		assertThat(bundle.participants()).hasSize(2);
		BattleSettlementService.ParticipantAnswers first = bundle.participants().get(0);
		assertThat(first.userId()).isEqualTo(10L);
		assertThat(first.discipleId()).isEqualTo(100L);
		assertThat(first.snapshotNoteIds()).containsExactly(7L, 8L);
		assertThat(first.answers()).extracting(BattleSettlementService.PendingAnswer::questionId).containsExactly(901L, 902L, 903L);
		assertThat(first.answers()).extracting(BattleSettlementService.PendingAnswer::answerId).containsExactly(100L, 101L, 102L);
		assertThat(first.answers().get(0).citedNoteIds()).containsExactly(7L);
	}

	@Test
	void 풀이가_끝나지_않았거나_RUNNING이_아니면_채점할_수_없다() {
		friends = BattleParticipant.joined(BATTLE_ID, 11L, 110L, false, NOW);
		ReflectionTestUtils.setField(friends, "id", 6L);
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
		assertThat(service.loadForGrading(BATTLE_ID)).isEmpty();

		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(solvedParticipant(5L, 10L)));
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		assertThat(service.loadForGrading(BATTLE_ID)).isEmpty();
		when(battleRepository.findById(404L)).thenReturn(Optional.empty());
		assertThat(service.loadForGrading(404L)).isEmpty();
	}

	@Test
	void 이미_채점된_답만_남았으면_채점할_것이_없다() {
		for (BattleAnswer answer : myAnswers) {
			answer.applyGrade(Verdict.CORRECT, JudgedBy.CODE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, false, null, "{}");
		}
		for (BattleAnswer answer : friendAnswers) {
			answer.applyGrade(Verdict.CORRECT, JudgedBy.CODE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, false, null, "{}");
		}

		assertThat(service.loadForGrading(BATTLE_ID)).isEmpty();
	}

	// --- settle ---

	@Test
	void 반영하면_답에_판정과_점수가_채워지고_참가자_합계와_건수가_계산된다() {
		boolean finished = service.settle(BATTLE_ID, all(
			grades(myAnswers, Verdict.CORRECT, "1.00", Verdict.PARTIAL, "0.50", Verdict.WRONG, "-0.50"),
			grades(friendAnswers, Verdict.UNKNOWN, "0.00", Verdict.ZEROED_INJECTION, "0.00", Verdict.CORRECT, "0.75")));

		assertThat(finished).isTrue();
		assertThat(myAnswers.get(0).getVerdict()).isEqualTo(Verdict.CORRECT);
		assertThat(myAnswers.get(2).getCauseSuggestion()).isEqualTo(ReviewCause.NOT_TAUGHT);
		assertThat(friendAnswers.get(1).isInjectionFlag()).isTrue();

		assertThat(mine.getTotalScore()).isEqualByComparingTo("1.00");
		assertThat(mine.getCorrectCount()).isEqualTo(1);
		assertThat(mine.getPartialCount()).isEqualTo(1);
		assertThat(mine.getWrongCount()).isEqualTo(1);
		assertThat(mine.getUnknownCount()).isZero();
		assertThat(friends.getTotalScore()).isEqualByComparingTo("0.75");
		assertThat(friends.getUnknownCount()).isEqualTo(1);
		assertThat(friends.getFlaggedCount()).isEqualTo(1);
		assertThat(friends.getCorrectCount()).isEqualTo(1);
	}

	@Test
	void 점수가_높은_쪽이_이기고_배틀은_FINISHED가_되어_알린다() {
		service.settle(BATTLE_ID, all(
			grades(myAnswers, Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00", Verdict.WRONG, "-0.50"),
			grades(friendAnswers, Verdict.CORRECT, "1.00", Verdict.WRONG, "-0.50", Verdict.UNKNOWN, "0.00")));

		assertThat(mine.getResult()).isEqualTo(BattleResult.WIN);
		assertThat(friends.getResult()).isEqualTo(BattleResult.LOSE);
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
		assertThat(battle.getFinishedAt()).isEqualTo(NOW);
		verify(eventPublisher).publishEvent(new BattleFinishedEvent(BATTLE_ID, BattleMode.RANKED));
	}

	@Test
	void 점수가_같으면_무승부다() {
		service.settle(BATTLE_ID, all(
			grades(myAnswers, Verdict.CORRECT, "1.00", Verdict.UNKNOWN, "0.00", Verdict.UNKNOWN, "0.00"),
			grades(friendAnswers, Verdict.UNKNOWN, "0.00", Verdict.CORRECT, "1.00", Verdict.UNKNOWN, "0.00")));

		assertThat(mine.getResult()).isEqualTo(BattleResult.DRAW);
		assertThat(friends.getResult()).isEqualTo(BattleResult.DRAW);
	}

	@Test
	void 승패는_소수_둘째_자리까지_비교한다() {
		service.settle(BATTLE_ID, all(
			grades(myAnswers, Verdict.CORRECT, "0.88", Verdict.UNKNOWN, "0.00", Verdict.UNKNOWN, "0.00"),
			grades(friendAnswers, Verdict.CORRECT, "0.87", Verdict.UNKNOWN, "0.00", Verdict.UNKNOWN, "0.00")));

		assertThat(mine.getResult()).isEqualTo(BattleResult.WIN);
		assertThat(friends.getResult()).isEqualTo(BattleResult.LOSE);
	}

	@Test
	void 연습_배틀은_승패를_만들지_않는다() {
		Battle practice = Battle.startPractice(1L, 2L, 9L, 3, 10L, NOW);
		ReflectionTestUtils.setField(practice, "id", BATTLE_ID);
		when(battleRepository.findLockedById(BATTLE_ID)).thenReturn(Optional.of(practice));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine));
		when(answerRepository.findByParticipantIdIn(anyCollection())).thenReturn(myAnswers);

		service.settle(BATTLE_ID, grades(myAnswers, Verdict.CORRECT, "1.00", Verdict.WRONG, "-0.50", Verdict.UNKNOWN, "0.00"));

		assertThat(mine.getResult()).isNull();
		assertThat(mine.getTotalScore()).isEqualByComparingTo("0.50");
		assertThat(practice.getStatus()).isEqualTo(BattleStatus.FINISHED);
		verify(eventPublisher).publishEvent(new BattleFinishedEvent(BATTLE_ID, BattleMode.PRACTICE));
	}

	@Test
	void 이미_끝난_배틀은_다시_반영하지_않는다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);

		boolean finished = service.settle(BATTLE_ID, all(
			grades(myAnswers, Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00"),
			grades(friendAnswers, Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00")));

		assertThat(finished).isFalse();
		assertThat(myAnswers.get(0).getVerdict()).isEqualTo(Verdict.PENDING);
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 채점_결과가_빠진_답이_있으면_예외로_되돌리고_배틀은_RUNNING_그대로다() {
		List<BattleSettlementService.AnswerGrade> missing = all(
			grades(myAnswers, Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00", Verdict.CORRECT, "1.00"),
			List.of(grade(friendAnswers.get(0), Verdict.CORRECT, "1.00")));   // 상대 답 2개가 빠졌다

		Throwable thrown = catchThrowable(() -> service.settle(BATTLE_ID, missing));

		assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("UNGRADED_ANSWER");
		verifyNoInteractions(eventPublisher);
	}

	@Test
	void 풀이가_끝나지_않은_참가자가_있으면_반영하지_않는다() {
		BattleParticipant unsolved = BattleParticipant.joined(BATTLE_ID, 11L, 110L, false, NOW);
		ReflectionTestUtils.setField(unsolved, "id", 6L);
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, unsolved));

		assertThat(service.settle(BATTLE_ID, List.of())).isFalse();
		assertThat(battle.getStatus()).isEqualTo(BattleStatus.RUNNING);
	}

	@Test
	void 이미_채점된_답은_건드리지_않고_남은_답만_반영한다() {
		myAnswers.get(0).applyGrade(Verdict.CORRECT, JudgedBy.CODE, new BigDecimal("1.00"), BigDecimal.ZERO,
			BigDecimal.ONE, new BigDecimal("1.00"), false, null, "{}");

		boolean finished = service.settle(BATTLE_ID, all(
			List.of(grade(myAnswers.get(1), Verdict.WRONG, "-0.50"), grade(myAnswers.get(2), Verdict.UNKNOWN, "0.00")),
			grades(friendAnswers, Verdict.UNKNOWN, "0.00", Verdict.UNKNOWN, "0.00", Verdict.UNKNOWN, "0.00")));

		assertThat(finished).isTrue();
		assertThat(mine.getTotalScore()).isEqualByComparingTo("0.50");
	}

}
