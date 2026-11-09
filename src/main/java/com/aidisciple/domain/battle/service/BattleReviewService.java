package com.aidisciple.domain.battle.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.ReviewAnswerRef;
import com.aidisciple.domain.battle.dto.ReviewableBattle;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.logic.NoteIdsJson;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * <b>복기(review 도메인)가 배틀을 읽고 쓰는 유일한 창구.</b> 끝난 RANKED 배틀의 참가자만 쓸 수 있다.
 * 검사 순서는 B-07 과 같다: 없는 배틀·남의 배틀·남의 답은 NOT_FOUND → 연습 배틀은 INVALID_INPUT → 안 끝났으면 BATTLE_NOT_FINISHED.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleReviewService {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQuestionRepository questionRepository;
	private final BattleAnswerRepository answerRepository;
	private final Clock clock;

	/** R-01: 내 답 전부(맞힌 문항 포함)를 seq 순으로 */
	public ReviewableBattle loadForReview(long userId, long battleId) {
		Battle battle = battleRepository.findById(battleId).orElseThrow(BattleReviewService::notFound);
		BattleParticipant me = participantRepository.findByBattleIdAndUserId(battleId, userId)
			.orElseThrow(BattleReviewService::notFound);
		requireFinishedRanked(battle);

		Map<Long, BattleQuestion> questions = questionRepository.findByBattleIdOrderBySeqAsc(battleId).stream()
			.collect(Collectors.toMap(BattleQuestion::getId, Function.identity()));
		List<ReviewableBattle.Answer> answers = answerRepository.findByParticipantId(me.getId()).stream()
			.filter(answer -> questions.containsKey(answer.getBattleQuestionId()))
			.map(answer -> toAnswer(answer, questions.get(answer.getBattleQuestionId())))
			.sorted(Comparator.comparingInt(ReviewableBattle.Answer::seq))
			.toList();
		return new ReviewableBattle(battleId, me.getAnswersRevealedAt(), answers);
	}

	/** R-02·R-03: 내 답인지 확인한다. 남의 답과 없는 답은 같은 NOT_FOUND 다. */
	public ReviewAnswerRef requireOwnAnswer(long userId, long battleAnswerId) {
		BattleAnswer answer = answerRepository.findById(battleAnswerId).orElseThrow(BattleReviewService::notFound);
		BattleParticipant owner = participantRepository.findById(answer.getParticipantId())
			.filter(participant -> participant.getUserId() == userId)
			.orElseThrow(BattleReviewService::notFound);
		Battle battle = battleRepository.findById(owner.getBattleId()).orElseThrow(BattleReviewService::notFound);
		requireFinishedRanked(battle);
		BattleQuestion question = questionRepository.findById(answer.getBattleQuestionId())
			.orElseThrow(BattleReviewService::notFound);
		return new ReviewAnswerRef(answer.getId(), question.getQuestionId(), answer.getVerdict());
	}

	/**
	 * R-11 정답 보기. 배틀 행을 먼저 잠가 연타·연습 시작(B-10)과 줄을 세운다.
	 * 이미 열었으면 처음 연 시각을 그대로 돌려준다(멱등). 상대 참가 행은 건드리지 않는다.
	 */
	@Transactional
	public Instant revealAnswers(long userId, long battleId) {
		Battle battle = battleRepository.findLockedById(battleId).orElseThrow(BattleReviewService::notFound);
		BattleParticipant me = participantRepository.findByBattleIdAndUserId(battleId, userId)
			.orElseThrow(BattleReviewService::notFound);
		requireFinishedRanked(battle);
		return me.revealAnswers(clock.instant().truncatedTo(ChronoUnit.MICROS));
	}
	private static void requireFinishedRanked(Battle battle) {
		if (battle.isPractice()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (battle.getStatus() != BattleStatus.FINISHED) {
			throw new BusinessException(ErrorCode.BATTLE_NOT_FINISHED);
		}
	}

	private static ReviewableBattle.Answer toAnswer(BattleAnswer answer, BattleQuestion question) {
		return new ReviewableBattle.Answer(answer.getId(), question.getSeq(), question.getQuestionId(),
			answer.getAnswerText(), answer.isUnknown(), answer.getVerdict(), answer.getFinalScore(),
			answer.getCopyFactor(), answer.isInjectionFlag(), answer.getCauseSuggestion(),
			NoteIdsJson.read(answer.getCitedNoteIdsJson()));
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.NOT_FOUND);
	}

}
