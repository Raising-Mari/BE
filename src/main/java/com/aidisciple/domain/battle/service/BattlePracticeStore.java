package com.aidisciple.domain.battle.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.battle.dto.CreatePracticeRequest;
import com.aidisciple.domain.battle.dto.CreatePracticeResponse;
import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.logic.NoteIdsJson;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.course.dto.DiscipleRef;
import com.aidisciple.domain.course.service.DiscipleService;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * 연습 배틀(B-10)의 DB 단계. 랭킹 배틀의 복기에서 고른 답의 <b>같은 문항</b>을 그대로 다시 낸다(새 문항을 뽑지 않는다).
 * 대기실이 없고 참가자는 나 하나이며, 만들 때 이미 RUNNING 이다. 제자는 <b>지금의 노트</b>로 푼다(0개여도 된다).
 * Elo·전적·순위와 {@code user_question_history} 는 건드리지 않는다 — 같은 문항은 원본 랭킹 배틀에서 이미 기록돼 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattlePracticeStore {

	/** 생략했을 때 "맞히지 못한" 답: 정답이 아닌 것 중 부정행위 의심(ZEROED_INJECTION)은 뺀다. */
	private static final Set<Verdict> MISSED = EnumSet.of(Verdict.WRONG, Verdict.PARTIAL, Verdict.UNKNOWN, Verdict.UNJUDGED);

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQuestionRepository questionRepository;
	private final BattleAnswerRepository answerRepository;
	private final UserLookupService userLookupService;
	private final DiscipleService discipleService;
	private final NoteLookupService noteLookupService;
	private final QuestionBankService questionBankService;
	private final Clock clock;

	/** {@code participantId} 는 커밋 뒤 풀이를 접수할 참가자 */
	public record Started(CreatePracticeResponse response, long participantId) {
	}

	@Transactional
	public Started create(long userId, CreatePracticeRequest request) {
		userLookupService.lockActive(userId);
		Instant now = clock.instant();

		Battle source = battleRepository.findById(request.sourceBattleId())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		BattleParticipant sourceMe = participantRepository.findByBattleIdAndUserId(source.getId(), userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (source.isPractice()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "연습 배틀은 다시 연습 배틀의 원본으로 삼을 수 없습니다.");
		}
		if (source.getStatus() != BattleStatus.FINISHED) {
			throw new BusinessException(ErrorCode.BATTLE_NOT_FINISHED);
		}
		if (sourceMe.getAnswersRevealedAt() != null) {
			throw new BusinessException(ErrorCode.PRACTICE_ANSWERS_REVEALED);
		}
		if (battleRepository.hasInProgress(userId, BattleMode.PRACTICE, now)) {
			throw new BusinessException(ErrorCode.BATTLE_ALREADY_ACTIVE);
		}
		DiscipleRef disciple = discipleService.findByUserAndCourse(userId, source.getCourseId())
			.orElseThrow(() -> new BusinessException(ErrorCode.BATTLE_DISCIPLE_REQUIRED));

		Map<Long, BattleQuestion> sourceQuestions = questionRepository.findByBattleIdOrderBySeqAsc(source.getId()).stream()
			.collect(Collectors.toMap(BattleQuestion::getId, Function.identity()));
		List<BattleAnswer> targets = targetAnswers(sourceMe, request.battleAnswerIds(), sourceQuestions);

		Set<Long> usable = questionBankService.practiceUsable(targets.stream()
			.map(answer -> sourceQuestions.get(answer.getBattleQuestionId()).getQuestionId()).toList());
		List<BattleAnswer> kept = new ArrayList<>();
		List<Long> skipped = new ArrayList<>();
		for (BattleAnswer answer : targets) {
			if (usable.contains(sourceQuestions.get(answer.getBattleQuestionId()).getQuestionId())) {
				kept.add(answer);
			} else {
				skipped.add(answer.getId());
			}
		}
		if (kept.isEmpty()) {
			throw new BusinessException(ErrorCode.PRACTICE_NOTHING_TO_REVIEW);
		}

		Battle practice = battleRepository.saveAndFlush(Battle.startPractice(source.getCourseId(), source.getUnitId(),
			source.getId(), kept.size(), userId, now));
		List<NoteRef> notes = noteLookupService.activeNotes(disciple.id(), source.getUnitId());
		BattleParticipant participant = participantRepository.save(BattleParticipant.practice(practice.getId(), userId,
			disciple.id(), NoteIdsJson.write(notes.stream().map(NoteRef::id).toList()), notes.size(), now));
		for (int i = 0; i < kept.size(); i++) {
			BattleAnswer answer = kept.get(i);
			questionRepository.save(BattleQuestion.of(practice.getId(),
				sourceQuestions.get(answer.getBattleQuestionId()).getQuestionId(), i + 1, answer.getId()));
		}
		return new Started(new CreatePracticeResponse(practice.getId(), practice.getMode(), practice.getStatus(), kept.size(),
			skipped), participant.getId());
	}

	/**
	 * 다시 풀 답. 생략하면 맞히지 못한 답 전부, 주면 그 답들만(맞힌 답도 고를 수 있다). 내 답이 아닌 id 가 섞여 있으면 NOT_FOUND.
	 * 원본 배틀의 문항 순서대로 돌려준다.
	 */
	private List<BattleAnswer> targetAnswers(BattleParticipant sourceMe, List<Long> requestedIds,
											 Map<Long, BattleQuestion> sourceQuestions) {
		List<BattleAnswer> mine = answerRepository.findByParticipantId(sourceMe.getId());
		List<BattleAnswer> chosen;
		if (requestedIds == null) {
			chosen = mine.stream().filter(answer -> MISSED.contains(answer.getVerdict())).toList();
		} else {
			Map<Long, BattleAnswer> byId = mine.stream().collect(Collectors.toMap(BattleAnswer::getId, Function.identity()));
			chosen = new ArrayList<>();
			for (Long id : new LinkedHashSet<>(requestedIds)) {
				BattleAnswer answer = byId.get(id);
				if (answer == null) {
					throw new BusinessException(ErrorCode.NOT_FOUND);
				}
				chosen.add(answer);
			}
		}
		return chosen.stream()
			.filter(answer -> sourceQuestions.containsKey(answer.getBattleQuestionId()))
			.sorted(Comparator.comparingInt(answer -> sourceQuestions.get(answer.getBattleQuestionId()).getSeq()))
			.toList();
	}

}
