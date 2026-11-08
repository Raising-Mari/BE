package com.aidisciple.domain.battle.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleResult;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.JudgedBy;
import com.aidisciple.domain.battle.entity.SolveStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.event.BattleFinishedEvent;
import com.aidisciple.domain.battle.logic.NoteIdsJson;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.review.entity.ReviewCause;

/**
 * 채점(grading 도메인)이 배틀을 읽고 결과를 쓰는 창구. 채점은 {@code battle_answers} 와 배틀 상태를 직접 만지지 않고 이 서비스만 부른다.
 * 읽기({@link #loadForGrading})와 쓰기({@link #settle}) 사이에 AI 를 부르므로 둘은 서로 다른 트랜잭션이다.
 * 쓰기는 배틀 행을 잠그고 한 트랜잭션에서 답·참가자 점수·승패·{@code FINISHED} 를 함께 바꾼다 — 중간까지만 반영되는 일이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleSettlementService {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQuestionRepository questionRepository;
	private final BattleAnswerRepository answerRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/** 채점할 배틀 하나. 참가자마다 채점 전(PENDING)인 답과, 그 참가자가 준비 완료 때 스냅샷한 노트 id 가 들어 있다. */
	public record GradingBundle(long battleId, long courseId, long unitId, BattleMode mode,
								List<ParticipantAnswers> participants) {
	}

	public record ParticipantAnswers(long participantId, long userId, long discipleId, List<Long> snapshotNoteIds,
									 List<PendingAnswer> answers) {
	}

	public record PendingAnswer(long answerId, long questionId, int seq, String answerText, boolean unknown,
								List<Long> citedNoteIds) {
	}

	/** 답 하나의 채점 결과. 채점이 만들어 {@link #settle} 에 넘긴다. */
	public record AnswerGrade(long answerId, Verdict verdict, JudgedBy judgedBy, BigDecimal rawScore, BigDecimal copyRatio,
							  BigDecimal copyFactor, BigDecimal finalScore, boolean injectionFlag, ReviewCause causeSuggestion,
							  String detailJson) {
	}

	/**
	 * 채점할 수 있는 상태의 배틀. RUNNING 이고 모든 참가자가 풀이를 마쳤으며 채점 전인 답이 있을 때만 있다.
	 * 이미 채점됐거나(FINISHED) 아직 풀이 중이거나 취소된 배틀은 빈 값이라, 같은 이벤트가 두 번 와도 두 번 채점하지 않는다.
	 */
	public Optional<GradingBundle> loadForGrading(long battleId) {
		Battle battle = battleRepository.findById(battleId).orElse(null);
		if (battle == null || battle.getStatus() != BattleStatus.RUNNING) {
			return Optional.empty();
		}
		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		if (participants.isEmpty() || participants.stream().anyMatch(p -> p.getSolveStatus() != SolveStatus.SOLVED)) {
			return Optional.empty();
		}
		Map<Long, BattleQuestion> questions = questionRepository.findByBattleIdOrderBySeqAsc(battleId).stream()
			.collect(Collectors.toMap(BattleQuestion::getId, Function.identity()));
		Map<Long, List<BattleAnswer>> answersByParticipant = answerRepository
			.findByParticipantIdIn(participants.stream().map(BattleParticipant::getId).toList()).stream()
			.collect(Collectors.groupingBy(BattleAnswer::getParticipantId));

		List<ParticipantAnswers> result = new ArrayList<>();
		boolean anyPending = false;
		for (BattleParticipant participant : participants) {
			List<PendingAnswer> pending = answersByParticipant.getOrDefault(participant.getId(), List.of()).stream()
				.filter(BattleAnswer::isPending)
				.filter(answer -> questions.containsKey(answer.getBattleQuestionId()))
				.map(answer -> {
					BattleQuestion question = questions.get(answer.getBattleQuestionId());
					return new PendingAnswer(answer.getId(), question.getQuestionId(), question.getSeq(), answer.getAnswerText(),
						answer.isUnknown(), NoteIdsJson.read(answer.getCitedNoteIdsJson()));
				})
				.sorted((a, b) -> Integer.compare(a.seq(), b.seq()))
				.toList();
			anyPending |= !pending.isEmpty();
			result.add(new ParticipantAnswers(participant.getId(), participant.getUserId(), participant.getDiscipleId(),
				NoteIdsJson.read(participant.getNoteIdsJson()), pending));
		}
		if (!anyPending) {
			return Optional.empty();
		}
		return Optional.of(new GradingBundle(battleId, battle.getCourseId(), battle.getUnitId(), battle.getMode(), result));
	}

	/**
	 * 채점 결과를 반영한다: 답에 판정·점수를 채우고, 참가자의 합계·건수·승패를 계산하고, 배틀을 FINISHED 로 만든 뒤 알린다
	 * ({@link BattleFinishedEvent}). 승패는 합계 점수(소수 둘째 자리)가 높은 쪽 승, 같으면 무승부이고 연습 배틀은 없다.
	 * 이미 끝났거나 아직 풀이 중이면 아무것도 하지 않고 false 다. 채점 전인 답에 결과가 하나라도 빠졌으면 예외로 되돌린다.
	 *
	 * @return 이번 호출로 FINISHED 가 됐으면 true
	 */
	@Transactional
	public boolean settle(long battleId, List<AnswerGrade> grades) {
		Battle battle = battleRepository.findLockedById(battleId).orElse(null);
		if (battle == null || battle.getStatus() != BattleStatus.RUNNING) {
			return false;
		}
		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		if (participants.stream().anyMatch(p -> p.getSolveStatus() != SolveStatus.SOLVED)) {
			return false;
		}
		Map<Long, AnswerGrade> gradeByAnswer = grades.stream()
			.collect(Collectors.toMap(AnswerGrade::answerId, Function.identity(), (first, second) -> first));
		Map<Long, List<BattleAnswer>> answersByParticipant = answerRepository
			.findByParticipantIdIn(participants.stream().map(BattleParticipant::getId).toList()).stream()
			.collect(Collectors.groupingBy(BattleAnswer::getParticipantId));

		for (BattleParticipant participant : participants) {
			for (BattleAnswer answer : answersByParticipant.getOrDefault(participant.getId(), List.of())) {
				if (!answer.isPending()) {
					continue;
				}
				AnswerGrade grade = gradeByAnswer.get(answer.getId());
				if (grade == null) {
					throw new IllegalStateException("UNGRADED_ANSWER");
				}
				answer.applyGrade(grade.verdict(), grade.judgedBy(), grade.rawScore(), grade.copyRatio(), grade.copyFactor(),
					grade.finalScore(), grade.injectionFlag(), grade.causeSuggestion(), grade.detailJson());
			}
		}

		for (BattleParticipant participant : participants) {
			summarize(participant, answersByParticipant.getOrDefault(participant.getId(), List.of()));
		}
		if (!battle.isPractice() && participants.size() == 2) {
			decideWinner(participants.get(0), participants.get(1));
		}
		battle.finish(clock.instant());
		eventPublisher.publishEvent(new BattleFinishedEvent(battleId, battle.getMode()));
		return true;
	}

	private static void summarize(BattleParticipant participant, List<BattleAnswer> answers) {
		BigDecimal total = answers.stream().map(BattleAnswer::getFinalScore).reduce(BigDecimal.ZERO, BigDecimal::add)
			.setScale(2, java.math.RoundingMode.HALF_UP);
		participant.applyResult(total, count(answers, Verdict.CORRECT), count(answers, Verdict.PARTIAL),
			count(answers, Verdict.WRONG), count(answers, Verdict.UNKNOWN), count(answers, Verdict.ZEROED_INJECTION), null);
	}

	private static int count(List<BattleAnswer> answers, Verdict verdict) {
		return (int) answers.stream().filter(answer -> answer.getVerdict() == verdict).count();
	}

	private static void decideWinner(BattleParticipant first, BattleParticipant second) {
		int comparison = first.getTotalScore().compareTo(second.getTotalScore());
		BattleResult firstResult = comparison > 0 ? BattleResult.WIN : comparison < 0 ? BattleResult.LOSE : BattleResult.DRAW;
		BattleResult secondResult = comparison > 0 ? BattleResult.LOSE : comparison < 0 ? BattleResult.WIN : BattleResult.DRAW;
		applyResultOnly(first, firstResult);
		applyResultOnly(second, secondResult);
	}

	private static void applyResultOnly(BattleParticipant participant, BattleResult result) {
		participant.applyResult(participant.getTotalScore(), participant.getCorrectCount(), participant.getPartialCount(),
			participant.getWrongCount(), participant.getUnknownCount(), participant.getFlaggedCount(), result);
	}

}
