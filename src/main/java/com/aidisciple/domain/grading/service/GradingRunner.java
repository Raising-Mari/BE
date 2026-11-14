package com.aidisciple.domain.grading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import com.aidisciple.domain.battle.entity.JudgedBy;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.battle.service.BattleSettlementService;
import com.aidisciple.domain.battle.service.BattleSettlementService.AnswerGrade;
import com.aidisciple.domain.battle.service.BattleSettlementService.GradingBundle;
import com.aidisciple.domain.battle.service.BattleSettlementService.ParticipantAnswers;
import com.aidisciple.domain.battle.service.BattleSettlementService.PendingAnswer;
import com.aidisciple.domain.grading.config.GradingProperties;
import com.aidisciple.domain.grading.config.ScoringProperties;
import com.aidisciple.domain.grading.logic.AnswerCodeGrader;
import com.aidisciple.domain.grading.logic.CauseSuggester;
import com.aidisciple.domain.grading.logic.CopyPenalty;
import com.aidisciple.domain.grading.logic.GradingDetail;
import com.aidisciple.domain.grading.logic.InjectionDetector;
import com.aidisciple.domain.grading.logic.ScoreCalculator;
import com.aidisciple.domain.material.service.MaterialSourceService;
import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.service.QuestionLookupService;
import com.aidisciple.domain.review.entity.ReviewCause;
import com.aidisciple.domain.teaching.dto.NoteRef;
import com.aidisciple.domain.teaching.service.NoteLookupService;

/**
 * 배틀 하나를 채점해 끝낸다(api-spec 11-4). <b>트랜잭션을 걸지 않는다</b>: AI 판정을 부르는 동안 DB 커넥션을 쥐지 않기 위해서다.
 * 읽기(채점할 답·문항·노트·자료)와 쓰기({@link BattleSettlementService#settle})는 각자 짧은 트랜잭션이다.
 * <p>
 * 순서: ① 코드로 모름·지시문·정답·오답을 가른다 → ② 코드로 못 가른 답만 AI 에 판정시킨다(배틀당 한 번, 많으면 나눠서)
 * → ③ 점수·복붙 보정·원인 제안을 계산한다 → ④ 한 트랜잭션으로 반영하고 배틀을 FINISHED 로 만든다.
 * 실패해도 배틀은 RUNNING 으로 남고(상태가 DB 에 있다) 서버가 다시 시작될 때 이어서 채점한다.
 * 로그에는 배틀 id 와 오류 종류만 남긴다 — 답·노트 내용은 남기지 않는다.
 */
@Slf4j
@Service
public class GradingRunner {

	static final String STAGE_UNKNOWN = "UNKNOWN";
	static final String STAGE_INJECTION = "INJECTION";
	static final String STAGE_CODE = "CODE";
	static final String STAGE_LLM = "LLM";
	static final String STAGE_LLM_FAILED = "LLM_FAILED";
	static final String STAGE_QUESTION_MISSING = "QUESTION_MISSING";

	private static final Set<Verdict> NEEDS_CAUSE = Set.of(Verdict.WRONG, Verdict.PARTIAL, Verdict.UNKNOWN, Verdict.UNJUDGED);

	private final BattleSettlementService settlementService;
	private final QuestionLookupService questionLookupService;
	private final NoteLookupService noteLookupService;
	private final MaterialSourceService materialSourceService;
	private final GradeBatchJudge judge;
	private final JsonMapper jsonMapper;
	private final AnswerCodeGrader codeGrader;
	private final CopyPenalty copyPenalty;
	private final ScoreCalculator scoreCalculator;
	private final CauseSuggester causeSuggester;

	public GradingRunner(BattleSettlementService settlementService, QuestionLookupService questionLookupService,
						 NoteLookupService noteLookupService, MaterialSourceService materialSourceService, GradeBatchJudge judge,
						 JsonMapper jsonMapper, GradingProperties gradingProperties, ScoringProperties scoringProperties) {
		this.settlementService = settlementService;
		this.questionLookupService = questionLookupService;
		this.noteLookupService = noteLookupService;
		this.materialSourceService = materialSourceService;
		this.judge = judge;
		this.jsonMapper = jsonMapper;
		this.codeGrader = new AnswerCodeGrader(new InjectionDetector(gradingProperties.injectionPatterns()));
		this.copyPenalty = new CopyPenalty(scoringProperties);
		this.scoreCalculator = new ScoreCalculator(scoringProperties);
		this.causeSuggester = new CauseSuggester(gradingProperties.causeOverlapThreshold());
	}

	/** 채점할 수 있는 상태가 아니면(이미 끝났거나 풀이 중) 아무것도 하지 않는다 — 같은 이벤트가 두 번 와도 두 번 채점하지 않는다. */
	public void run(long battleId) {
		Optional<GradingBundle> loaded = settlementService.loadForGrading(battleId);
		if (loaded.isEmpty()) {
			return;
		}
		try {
			boolean finished = settlementService.settle(battleId, gradeAll(loaded.get()));
			if (finished) {
				log.info("배틀 채점 완료: battleId={}", battleId);
			}
		} catch (RuntimeException e) {
			log.warn("배틀 채점 실패: battleId={}, cause={}", battleId, e.getClass().getSimpleName());
		}
	}

	/** 채점 중인 답 하나와 그 답을 채점하는 데 쓴 값 */
	private record Work(ParticipantAnswers participant, PendingAnswer answer, GradingQuestion question, List<String> citedTexts,
						AnswerCodeGrader.Result code) {
	}

	private List<AnswerGrade> gradeAll(GradingBundle bundle) {
		Map<Long, GradingQuestion> questions = questionLookupService.gradingQuestions(bundle.participants().stream()
			.flatMap(participant -> participant.answers().stream()).map(PendingAnswer::questionId).collect(Collectors.toSet()));
		Map<Long, String> noteTexts = noteTexts(bundle);
		Set<String> materialGrams = copyPenalty.materialGrams(materialSourceService.doneSentencesOfUnit(bundle.unitId()).stream()
			.map(MaterialSourceService.UnitSentence::text).toList());

		// ① 코드 채점
		List<Work> works = new ArrayList<>();
		for (ParticipantAnswers participant : bundle.participants()) {
			for (PendingAnswer answer : participant.answers()) {
				GradingQuestion question = questions.get(answer.questionId());
				List<String> cited = answer.citedNoteIds().stream().map(noteTexts::get).filter(java.util.Objects::nonNull).toList();
				AnswerCodeGrader.Result code = question == null ? null : codeGrader.grade(
					new AnswerCodeGrader.Input(answer.answerText(), answer.unknown(), question, cited));
				works.add(new Work(participant, answer, question, cited, code));
			}
		}

		// ② 코드로 못 가른 답만 AI 판정
		List<GradeBatchJudge.Item> ambiguous = works.stream()
			.filter(work -> work.code() != null && work.code().ambiguous())
			.map(work -> new GradeBatchJudge.Item(work.answer().answerId(), work.question(), work.answer().answerText()))
			.toList();
		Map<Long, GradeBatchJudge.Judgment> judgments = ambiguous.isEmpty() ? Map.of() : judge.judge(bundle.battleId(), ambiguous);

		// ③ 점수·복붙 보정·원인 제안
		Map<Long, Supplier<List<String>>> messagesByParticipant = new java.util.HashMap<>();
		for (ParticipantAnswers participant : bundle.participants()) {
			messagesByParticipant.put(participant.participantId(),
				memoize(() -> noteLookupService.studentMessages(participant.discipleId(), bundle.unitId())));
		}
		List<AnswerGrade> grades = new ArrayList<>();
		for (Work work : works) {
			grades.add(toGrade(work, judgments.get(work.answer().answerId()), materialGrams, noteTexts,
				messagesByParticipant.get(work.participant().participantId())));
		}
		return grades;
	}

	private AnswerGrade toGrade(Work work, GradeBatchJudge.Judgment judgment, Set<String> materialGrams, Map<Long, String> noteTexts,
								Supplier<List<String>> studentMessages) {
		Verdict verdict;
		JudgedBy judgedBy;
		String stage;
		String reason = null;
		AnswerCodeGrader.Result code = work.code();
		if (code == null) {
			verdict = Verdict.UNJUDGED;
			judgedBy = JudgedBy.CODE;
			stage = STAGE_QUESTION_MISSING;
		} else if (!code.ambiguous()) {
			verdict = code.verdict();
			judgedBy = JudgedBy.CODE;
			stage = verdict == Verdict.UNKNOWN ? STAGE_UNKNOWN : verdict == Verdict.ZEROED_INJECTION ? STAGE_INJECTION : STAGE_CODE;
		} else {
			verdict = judgment.verdict();
			judgedBy = JudgedBy.LLM;
			stage = verdict == Verdict.UNJUDGED ? STAGE_LLM_FAILED : STAGE_LLM;
			reason = judgment.reason();
		}

		CopyPenalty.Ratios ratios = verdict == Verdict.UNKNOWN || verdict == Verdict.ZEROED_INJECTION
			? new CopyPenalty.Ratios(0, 0, 0)
			: copyPenalty.ratios(materialGrams, work.answer().answerText(), work.citedTexts());
		BigDecimal factor = verdict == Verdict.CORRECT || verdict == Verdict.PARTIAL
			? copyPenalty.factor(ratios.combined()) : BigDecimal.ONE.setScale(2);
		GradingMode mode = work.question() == null ? GradingMode.ANSWER_LIST : work.question().gradingMode();
		BigDecimal raw = scoreCalculator.rawScore(verdict, mode);
		BigDecimal finalScore = scoreCalculator.finalScore(verdict, raw, factor);

		ReviewCause cause = null;
		if (NEEDS_CAUSE.contains(verdict) && work.question() != null) {
			List<String> snapshotNotes = work.participant().snapshotNoteIds().stream().map(noteTexts::get)
				.filter(java.util.Objects::nonNull).toList();
			cause = causeSuggester.suggest(work.question(), snapshotNotes, studentMessages);
		}

		GradingDetail detail = new GradingDetail(stage, code == null || code.codeVerdict() == null ? null : code.codeVerdict().name(),
			code == null ? List.of() : code.matchedElements(), code == null ? List.of() : code.missingElements(),
			code == null ? List.of() : code.elementsInNotes(), code == null ? List.of() : code.injectionPatterns(),
			round3(ratios.answer()).doubleValue(), round3(ratios.notes()).doubleValue(), reason);
		return new AnswerGrade(work.answer().answerId(), verdict, judgedBy, raw, round3(ratios.combined()), factor, finalScore,
			verdict == Verdict.ZEROED_INJECTION, cause, jsonMapper.writeValueAsString(detail));
	}

	/** 답과 스냅샷에 쓰인 노트 id 전체를 한 번에 읽는다. 노트는 한 번 저장하면 바뀌지 않으므로 그 순간의 내용이다. */
	private Map<Long, String> noteTexts(GradingBundle bundle) {
		Set<Long> ids = new HashSet<>();
		for (ParticipantAnswers participant : bundle.participants()) {
			ids.addAll(participant.snapshotNoteIds());
			participant.answers().forEach(answer -> ids.addAll(answer.citedNoteIds()));
		}
		return noteLookupService.findByIds(ids).stream().collect(Collectors.toMap(NoteRef::id, NoteRef::content));
	}

	private static BigDecimal round3(double value) {
		return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
	}

	private static <T> Supplier<T> memoize(Supplier<T> supplier) {
		return new Supplier<>() {
			private T value;
			private boolean loaded;

			@Override
			public T get() {
				if (!loaded) {
					value = supplier.get();
					loaded = true;
				}
				return value;
			}
		};
	}

}
