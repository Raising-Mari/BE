package com.aidisciple.domain.battle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.battle.dto.BattleResultResponse;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class BattleResultServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
	private static final long BATTLE_ID = 50L;
	private static final long ME = 10L;
	private static final long FRIEND = 11L;

	private final BattleRepository battleRepository = mock(BattleRepository.class);
	private final BattleParticipantRepository participantRepository = mock(BattleParticipantRepository.class);
	private final BattleQuestionRepository questionRepository = mock(BattleQuestionRepository.class);
	private final BattleAnswerRepository answerRepository = mock(BattleAnswerRepository.class);
	private final QuestionBankService questionBankService = mock(QuestionBankService.class);
	private final BattleEloLookup eloLookup = mock(BattleEloLookup.class);
	private final BattleResultService service = new BattleResultService(battleRepository, participantRepository,
		questionRepository, answerRepository, questionBankService, eloLookup);

	private Battle battle;
	private BattleParticipant mine;
	private BattleParticipant friends;

	@BeforeEach
	void 기본_상태() {
		battle = Battle.openRoom(1L, 2L, 2, ME, "K7M4QX", NOW);
		ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
		ReflectionTestUtils.setField(battle, "status", BattleStatus.FINISHED);
		mine = participant(5L, ME, new BigDecimal("1.50"), BattleResult.WIN, 1, 1, 0, 0);
		friends = participant(6L, FRIEND, new BigDecimal("0.00"), BattleResult.LOSE, 0, 0, 1, 1);
		when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
		when(participantRepository.findByBattleIdOrderByIdAsc(BATTLE_ID)).thenReturn(List.of(mine, friends));
		when(questionRepository.findByBattleIdOrderBySeqAsc(BATTLE_ID)).thenReturn(List.of(
			battleQuestion(201L, 901L, 1, null), battleQuestion(202L, 902L, 2, null)));
		when(questionBankService.stems(anyCollection())).thenReturn(Map.of(901L, "스택이란?", 902L, "큐란?"));
		when(answerRepository.findByParticipantId(5L)).thenReturn(List.of(
			answer(301L, 5L, 201L, "후입선출", Verdict.CORRECT, "1.00"), answer(302L, 5L, 202L, "선입선출", Verdict.PARTIAL, "0.50")));
		when(answerRepository.findByParticipantId(6L)).thenReturn(List.of(
			answer(401L, 6L, 201L, "몰라요 아마 큐", Verdict.WRONG, "-0.50"), answer(402L, 6L, 202L, null, Verdict.UNKNOWN, "0.00")));
	}

	private static BattleParticipant participant(long id, long userId, BigDecimal total, BattleResult result, int correct, int partial,
												int wrong, int unknown) {
		BattleParticipant participant = BattleParticipant.joined(BATTLE_ID, userId, userId * 10, id == 5L, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		participant.applyResult(total, correct, partial, wrong, unknown, 0, result);
		return participant;
	}

	private static BattleQuestion battleQuestion(long id, long questionId, int seq, Long sourceAnswerId) {
		BattleQuestion question = BattleQuestion.of(BATTLE_ID, questionId, seq, sourceAnswerId);
		ReflectionTestUtils.setField(question, "id", id);
		return question;
	}

	private static BattleAnswer answer(long id, long participantId, long battleQuestionId, String text, Verdict verdict, String score) {
		BattleAnswer answer = BattleAnswer.solved(participantId, battleQuestionId, text, text == null, "[]");
		ReflectionTestUtils.setField(answer, "id", id);
		ReflectionTestUtils.setField(answer, "verdict", verdict);
		ReflectionTestUtils.setField(answer, "finalScore", new BigDecimal(score));
		return answer;
	}

	@Test
	void 끝난_배틀의_결과는_내_점수와_상대_점수와_승패와_문항별_판정이다() {
		BattleResultResponse result = service.get(ME, BATTLE_ID);

		assertThat(result.mode()).isEqualTo(BattleMode.RANKED);
		assertThat(result.sourceBattleId()).isNull();
		assertThat(result.result()).isEqualTo(BattleResult.WIN);
		assertThat(result.me()).isEqualTo(new BattleResultResponse.ScoreSummary(new BigDecimal("1.50"), 1, 1, 0, 0));
		assertThat(result.opponent()).isEqualTo(new BattleResultResponse.ScoreSummary(new BigDecimal("0.00"), 0, 0, 1, 1));
		assertThat(result.elo()).isNull();   // 반영 전(조회 결과 없음)이면 null
		assertThat(result.questions()).extracting(BattleResultResponse.QuestionResult::seq).containsExactly(1, 2);
		assertThat(result.questions()).extracting(BattleResultResponse.QuestionResult::stem).containsExactly("스택이란?", "큐란?");

		BattleResultResponse.QuestionResult first = result.questions().get(0);
		assertThat(first.myVerdict()).isEqualTo(Verdict.CORRECT);
		assertThat(first.myScore()).isEqualByComparingTo("1.00");
		assertThat(first.myAnswerText()).isEqualTo("후입선출");
		assertThat(first.opponentVerdict()).isEqualTo(Verdict.WRONG);
		assertThat(first.opponentScore()).isEqualByComparingTo("-0.50");
		assertThat(first.retestOf()).isNull();
	}

	@Test
	void 랭킹_배틀의_결과에는_내_Elo_변화가_있고_반영_전이면_null이다() {
		when(eloLookup.find(BATTLE_ID, ME)).thenReturn(Optional.of(new BattleEloLookup.EloChange(1000, 1016, 16)));

		BattleResultResponse withElo = service.get(ME, BATTLE_ID);
		BattleResultResponse notYet = service.get(FRIEND, BATTLE_ID);

		assertThat(withElo.elo()).isEqualTo(new BattleResultResponse.Elo(1000, 1016, 16));
		assertThat(notYet.elo()).isNull();
	}

	@Test
	void 상대의_승패는_상대_쪽에서_뒤집혀_보인다() {
		BattleResultResponse result = service.get(FRIEND, BATTLE_ID);

		assertThat(result.result()).isEqualTo(BattleResult.LOSE);
		assertThat(result.me().totalScore()).isEqualByComparingTo("0.00");
		assertThat(result.opponent().totalScore()).isEqualByComparingTo("1.50");
	}

	@Test
	void 상대의_답_원문은_두_사람이_모두_공개에_동의했을_때만_준다() {
		assertThat(service.get(ME, BATTLE_ID).questions()).extracting(BattleResultResponse.QuestionResult::opponentAnswerText)
			.containsOnlyNulls();

		mine.changeNotesPublic(true);
		assertThat(service.get(ME, BATTLE_ID).questions()).extracting(BattleResultResponse.QuestionResult::opponentAnswerText)
			.containsOnlyNulls();   // 나만 동의

		friends.changeNotesPublic(true);
		List<BattleResultResponse.QuestionResult> shown = service.get(ME, BATTLE_ID).questions();
		assertThat(shown.get(0).opponentAnswerText()).isEqualTo("몰라요 아마 큐");
		assertThat(shown.get(1).opponentAnswerText()).isNull();   // 모름은 답 글이 없다
		assertThat(shown.get(0).opponentVerdict()).isEqualTo(Verdict.WRONG);
	}

	@Test
	void 끝나지_않은_배틀은_BATTLE_NOT_FINISHED다() {
		for (BattleStatus status : List.of(BattleStatus.RUNNING, BattleStatus.ERROR, BattleStatus.LOBBY, BattleStatus.CANCELED)) {
			ReflectionTestUtils.setField(battle, "status", status);

			Throwable thrown = catchThrowable(() -> service.get(ME, BATTLE_ID));

			assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BATTLE_NOT_FINISHED));
		}
	}

	@Test
	void 참가자가_아니거나_없는_배틀은_NOT_FOUND다() {
		when(battleRepository.findById(404L)).thenReturn(Optional.empty());

		for (Runnable action : List.<Runnable>of(() -> service.get(99L, BATTLE_ID), () -> service.get(ME, 404L))) {
			Throwable thrown = catchThrowable(action::run);
			assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
		}
	}

	@Test
	void 참가자가_아닌_사람에게는_끝나지_않은_배틀도_NOT_FOUND라_존재를_알_수_없다() {
		ReflectionTestUtils.setField(battle, "status", BattleStatus.RUNNING);

		Throwable thrown = catchThrowable(() -> service.get(99L, BATTLE_ID));

		assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void 연습_배틀은_상대와_승패와_Elo가_없고_원본_배틀과_다시_시험한_답을_준다() {
		Battle practice = Battle.startPractice(1L, 2L, 9L, 2, ME, NOW);
		ReflectionTestUtils.setField(practice, "id", 70L);
		ReflectionTestUtils.setField(practice, "status", BattleStatus.FINISHED);
		BattleParticipant only = BattleParticipant.practice(70L, ME, 100L, "[]", 0, NOW);
		ReflectionTestUtils.setField(only, "id", 7L);
		only.applyResult(new BigDecimal("1.00"), 1, 0, 1, 0, 0, null);
		when(battleRepository.findById(70L)).thenReturn(Optional.of(practice));
		when(participantRepository.findByBattleIdOrderByIdAsc(70L)).thenReturn(List.of(only));
		when(questionRepository.findByBattleIdOrderBySeqAsc(70L)).thenReturn(List.of(
			battleQuestion(501L, 901L, 1, 77L), battleQuestion(502L, 902L, 2, 80L)));
		when(answerRepository.findByParticipantId(7L)).thenReturn(List.of(
			answer(601L, 7L, 501L, "후입선출", Verdict.CORRECT, "1.00"), answer(602L, 7L, 502L, "선입선출", Verdict.WRONG, "-0.50")));
		when(answerRepository.findAllById(anyCollection())).thenReturn(List.of(
			answer(77L, 5L, 201L, "x", Verdict.WRONG, "-0.50"), answer(80L, 5L, 202L, "y", Verdict.UNKNOWN, "0.00")));

		BattleResultResponse result = service.get(ME, 70L);

		assertThat(result.mode()).isEqualTo(BattleMode.PRACTICE);
		assertThat(result.sourceBattleId()).isEqualTo(9L);
		assertThat(result.opponent()).isNull();
		assertThat(result.result()).isNull();
		assertThat(result.elo()).isNull();
		assertThat(result.me().totalScore()).isEqualByComparingTo("1.00");
		assertThat(result.questions().get(0).retestOf()).isEqualTo(new BattleResultResponse.RetestOf(77L, Verdict.WRONG));
		assertThat(result.questions().get(1).retestOf()).isEqualTo(new BattleResultResponse.RetestOf(80L, Verdict.UNKNOWN));
		assertThat(result.questions()).extracting(BattleResultResponse.QuestionResult::opponentVerdict).containsOnlyNulls();
	}

}
