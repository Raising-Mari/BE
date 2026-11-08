package com.aidisciple.domain.battle.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.dto.BattleResultResponse;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * B-07 결과. 끝난 배틀만(그 전에는 {@code BATTLE_NOT_FINISHED}), 참가자만 본다 — 없는 배틀과 남의 배틀은 같은 NOT_FOUND 다.
 * <b>모범 답과 자료 근거는 싣지 않는다</b>(정답 보기 R-11 뒤에 복기 API 에서 본다). 채점 근거 JSON(내부 패턴 목록)도 싣지 않는다.
 * 상대의 답 원문은 두 사람이 모두 노트 공개에 동의했을 때만 담는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleResultService {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQuestionRepository questionRepository;
	private final BattleAnswerRepository answerRepository;
	private final QuestionBankService questionBankService;

	public BattleResultResponse get(long userId, long battleId) {
		Battle battle = battleRepository.findById(battleId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		BattleParticipant me = participants.stream().filter(p -> p.getUserId() == userId).findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (battle.getStatus() != BattleStatus.FINISHED) {
			throw new BusinessException(ErrorCode.BATTLE_NOT_FINISHED);
		}
		BattleParticipant opponent = battle.isPractice() ? null
			: participants.stream().filter(p -> p.getUserId() != userId).findFirst().orElse(null);

		List<BattleQuestion> questions = questionRepository.findByBattleIdOrderBySeqAsc(battleId);
		Map<Long, String> stems = questionBankService.stems(questions.stream().map(BattleQuestion::getQuestionId).toList());
		Map<Long, BattleAnswer> mine = answersByQuestion(me);
		Map<Long, BattleAnswer> theirs = opponent == null ? Map.of() : answersByQuestion(opponent);
		boolean showOpponentText = opponent != null && me.isNotesPublic() && opponent.isNotesPublic();
		Map<Long, BattleAnswer> retested = retestedAnswers(battle, questions);

		List<BattleResultResponse.QuestionResult> items = questions.stream()
			.map(question -> toQuestionResult(question, stems.get(question.getQuestionId()), mine.get(question.getId()),
				theirs.get(question.getId()), showOpponentText,
				question.getSourceAnswerId() == null ? null : retested.get(question.getSourceAnswerId())))
			.toList();
		return new BattleResultResponse(battle.getMode(), battle.isPractice() ? battle.getSourceBattleId() : null,
			summary(me), opponent == null ? null : summary(opponent), me.getResult(), null, items);
	}

	private Map<Long, BattleAnswer> answersByQuestion(BattleParticipant participant) {
		return answerRepository.findByParticipantId(participant.getId()).stream()
			.collect(Collectors.toMap(BattleAnswer::getBattleQuestionId, Function.identity(), (first, second) -> first));
	}

	/** 연습 배틀에서 각 문항이 다시 시험하는 원본 배틀의 답 */
	private Map<Long, BattleAnswer> retestedAnswers(Battle battle, List<BattleQuestion> questions) {
		if (!battle.isPractice()) {
			return Map.of();
		}
		List<Long> sourceAnswerIds = questions.stream().map(BattleQuestion::getSourceAnswerId)
			.filter(java.util.Objects::nonNull).toList();
		return answerRepository.findAllById(sourceAnswerIds).stream()
			.collect(Collectors.toMap(BattleAnswer::getId, Function.identity()));
	}

	private static BattleResultResponse.ScoreSummary summary(BattleParticipant participant) {
		return new BattleResultResponse.ScoreSummary(participant.getTotalScore(), participant.getCorrectCount(),
			participant.getPartialCount(), participant.getWrongCount(), participant.getUnknownCount());
	}

	private static BattleResultResponse.QuestionResult toQuestionResult(BattleQuestion question, String stem, BattleAnswer mine,
																		BattleAnswer theirs, boolean showOpponentText,
																		BattleAnswer retested) {
		return new BattleResultResponse.QuestionResult(question.getSeq(), stem,
			mine == null ? null : mine.getAnswerText(),
			mine == null ? null : mine.getVerdict(),
			mine == null ? null : mine.getFinalScore(),
			showOpponentText && theirs != null ? theirs.getAnswerText() : null,
			theirs == null ? null : theirs.getVerdict(),
			theirs == null ? null : theirs.getFinalScore(),
			retested == null ? null : new BattleResultResponse.RetestOf(retested.getId(), retested.getVerdict()));
	}

}
