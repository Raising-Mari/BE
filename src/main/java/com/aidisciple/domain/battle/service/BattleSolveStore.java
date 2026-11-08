package com.aidisciple.domain.battle.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.battle.entity.Battle;
import com.aidisciple.domain.battle.entity.BattleAnswer;
import com.aidisciple.domain.battle.entity.BattleParticipant;
import com.aidisciple.domain.battle.entity.BattleQuestion;
import com.aidisciple.domain.battle.entity.BattleStatus;
import com.aidisciple.domain.battle.entity.SolveStatus;
import com.aidisciple.domain.battle.event.BattleSolvedEvent;
import com.aidisciple.domain.battle.logic.NoteIdsJson;
import com.aidisciple.domain.battle.repository.BattleAnswerRepository;
import com.aidisciple.domain.battle.repository.BattleParticipantRepository;
import com.aidisciple.domain.battle.repository.BattleQuestionRepository;
import com.aidisciple.domain.battle.repository.BattleRepository;
import com.aidisciple.domain.question.service.DiscipleSolver;
import com.aidisciple.domain.question.service.QuestionBankService;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;

/**
 * 제자 풀이(J-03)의 DB 단계. AI 는 부르지 않는다 — {@link BattleSolveRunner} 가 이 서비스와 AI 를 번갈아 부른다.
 * 모든 메서드가 배틀 행을 먼저 잠근다: 취소·재시도·다른 참가자의 풀이 완료와 겹쳐도 "둘 다 풀었는가" 같은 판단이 엇갈리지 않게.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BattleSolveStore {

	private final BattleRepository battleRepository;
	private final BattleParticipantRepository participantRepository;
	private final BattleQuestionRepository questionRepository;
	private final BattleAnswerRepository answerRepository;
	private final NoteLookupService noteLookupService;
	private final QuestionBankService questionBankService;
	private final ApplicationEventPublisher eventPublisher;

	/** 풀이에 필요한 값: 스냅샷 시점의 노트(id+내용)와 고정된 문항(id+본문). 강의자료는 없다. */
	public record SolveContext(long battleId, long participantId, long userId, List<NoteRef> notes,
							   List<DiscipleSolver.SolveQuestion> questions) {
	}

	/** 서버 재시작 복구가 이어서 해야 할 일 */
	public record Recovery(List<Long> participantIdsToSolve) {
	}

	/**
	 * 풀이를 시작한다. RUNNING 배틀의 PENDING 참가자만 선점하고, 이미 풀고 있거나 끝났거나 배틀이 취소됐으면 빈 값이다.
	 * 같은 작업이 두 번 접수돼도 한 번만 푼다.
	 */
	@Transactional
	public Optional<SolveContext> begin(long participantId) {
		BattleParticipant participant = participantRepository.findById(participantId).orElse(null);
		if (participant == null) {
			return Optional.empty();
		}
		Battle battle = battleRepository.findLockedById(participant.getBattleId()).orElse(null);
		if (battle == null || battle.getStatus() != BattleStatus.RUNNING || !participant.claimSolve()) {
			return Optional.empty();
		}
		List<NoteRef> notes = noteLookupService.findByIds(NoteIdsJson.read(participant.getNoteIdsJson()));
		List<BattleQuestion> battleQuestions = questionRepository.findByBattleIdOrderBySeqAsc(battle.getId());
		Map<Long, String> stems = questionBankService.stems(battleQuestions.stream().map(BattleQuestion::getQuestionId).toList());
		List<DiscipleSolver.SolveQuestion> questions = battleQuestions.stream()
			.map(question -> new DiscipleSolver.SolveQuestion(question.getQuestionId(), stems.getOrDefault(question.getQuestionId(), "")))
			.toList();
		return Optional.of(new SolveContext(battle.getId(), participantId, participant.getUserId(), notes, questions));
	}

	/**
	 * 풀이 결과를 저장한다. 둘 다 풀었고 배틀이 RUNNING 이면 채점을 시작하라고 알린다({@link BattleSolvedEvent}).
	 * 풀이 도중 배틀이 취소됐다면 버린다. ERROR 인 배틀에서 늦게 끝난 풀이는 저장해 둔다 — 재시도(B-09) 때 다시 풀지 않는다.
	 */
	@Transactional
	public void complete(long participantId, List<DiscipleSolver.SolvedAnswer> answers, int attempts) {
		BattleParticipant participant = participantRepository.findById(participantId).orElse(null);
		if (participant == null) {
			return;
		}
		Battle battle = battleRepository.findLockedById(participant.getBattleId()).orElse(null);
		if (battle == null || participant.getSolveStatus() != SolveStatus.SOLVING
			|| (battle.getStatus() != BattleStatus.RUNNING && battle.getStatus() != BattleStatus.ERROR)) {
			return;
		}
		Map<Long, Long> battleQuestionIds = questionRepository.findByBattleIdOrderBySeqAsc(battle.getId()).stream()
			.collect(Collectors.toMap(BattleQuestion::getQuestionId, BattleQuestion::getId));
		for (DiscipleSolver.SolvedAnswer answer : answers) {
			Long battleQuestionId = battleQuestionIds.get(answer.questionId());
			if (battleQuestionId == null) {
				continue;
			}
			answerRepository.save(BattleAnswer.solved(participantId, battleQuestionId, answer.answer(), answer.unknown(),
				NoteIdsJson.write(answer.usedNoteIds())));
		}
		participant.markSolved(attempts);

		if (battle.getStatus() == BattleStatus.RUNNING && allSolved(battle.getId())) {
			eventPublisher.publishEvent(new BattleSolvedEvent(battle.getId()));
		}
	}

	/**
	 * 풀이가 끝내 실패했다. 배틀이 RUNNING 이면 ERROR 로 바꾼다(Elo 는 바뀌지 않는다). 이미 푼·실패한 참가자는 건드리지 않는다.
	 *
	 * @param reason 오류 코드나 예외 클래스 이름만. 노트·답 내용이 섞이지 않게 한다.
	 */
	@Transactional
	public void fail(long participantId, String reason, int attempts) {
		BattleParticipant participant = participantRepository.findById(participantId).orElse(null);
		if (participant == null) {
			return;
		}
		Battle battle = battleRepository.findLockedById(participant.getBattleId()).orElse(null);
		if (battle == null || participant.getSolveStatus() == SolveStatus.SOLVED
			|| participant.getSolveStatus() == SolveStatus.FAILED) {
			return;
		}
		participant.markSolveFailed(attempts);
		if (battle.getStatus() == BattleStatus.RUNNING) {
			battle.markError(reason);
		}
	}

	/**
	 * 서버가 시작될 때 RUNNING 배틀 하나를 이어받는다: 풀던 중이던 참가자를 접수 상태로 되돌리고, 다시 풀어야 할 참가자를 돌려준다.
	 * 모두 풀었는데 채점 전이라면 채점 이벤트를 다시 낸다(이 트랜잭션이 커밋된 뒤에 받는다).
	 */
	@Transactional
	public Recovery recover(long battleId) {
		Battle battle = battleRepository.findLockedById(battleId).orElse(null);
		if (battle == null || battle.getStatus() != BattleStatus.RUNNING) {
			return new Recovery(List.of());
		}
		List<BattleParticipant> participants = participantRepository.findByBattleIdOrderByIdAsc(battleId);
		List<Long> toSolve = new ArrayList<>();
		for (BattleParticipant participant : participants) {
			participant.releaseSolving();
			if (participant.getSolveStatus() == SolveStatus.PENDING) {
				toSolve.add(participant.getId());
			}
		}
		if (toSolve.isEmpty() && participants.stream().allMatch(p -> p.getSolveStatus() == SolveStatus.SOLVED)) {
			eventPublisher.publishEvent(new BattleSolvedEvent(battleId));
		}
		return new Recovery(toSolve);
	}

	private boolean allSolved(long battleId) {
		return participantRepository.findByBattleIdOrderByIdAsc(battleId).stream()
			.allMatch(participant -> participant.getSolveStatus() == SolveStatus.SOLVED);
	}

}
