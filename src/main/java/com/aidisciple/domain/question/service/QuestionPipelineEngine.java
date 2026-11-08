package com.aidisciple.domain.question.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.LlmRequest;
import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.ValidationStage;
import com.aidisciple.domain.question.logic.CandidateFailure;
import com.aidisciple.domain.question.logic.CodeGrader;
import com.aidisciple.domain.question.logic.CodeVerdict;
import com.aidisciple.domain.question.logic.EvidenceChecker;
import com.aidisciple.domain.question.logic.QuestionCandidate;
import com.aidisciple.domain.question.logic.SentenceWindows;
import com.aidisciple.domain.question.logic.SourceSentence;

/**
 * 문제 은행 파이프라인의 한 라운드 (api-spec 11-2 의 1~3단계, AI 2회). DB 를 읽거나 쓰지 않는다.
 * 개념 추출(0단계)은 생략했고(backend-plan D-7), 외부 판정(4단계)은 보류라 통과 문항은 UNJUDGED 로 저장한다(저장은 4단계 몫).
 *  누출 시험은 전용 프롬프트(leak-test)로 "강의를 듣지 않은 사람"이 상식으로 풀게 해서, 맞히면 가르치지 않아도 풀리는 문항으로 보고 버린다.
 */
@Component
@RequiredArgsConstructor
public class QuestionPipelineEngine {

	static final String PROMPT_NAME = "question-generate";
	static final String PROMPT_VERSION = "v1";
	static final String RELATED_TYPE = "QUESTION_JOB";
	/** 누출 시험 전용 프롬프트: 강의를 듣지 않은 사람이 상식으로 풀게 한다 (battle-solve 에 빈 노트를 넣으면 항상 "모름"이라 거르지 못한다) */
	static final String LEAK_PROMPT_NAME = "leak-test";
	static final String LEAK_PROMPT_VERSION = "v1";
	/** 프롬프트에 넣는 기존 문항 수 상한 (중복 판정 자체는 전체 문항으로 한다) */
	private static final int EXISTING_STEMS_IN_PROMPT = 30;

	private final LlmClient llmClient;
	private final QuestionProperties properties;

	/**
	 * @param sentences     주차에 연결된 DONE 자료의 문장 전체. 번호(no)는 주차 전체에서 1부터 이어진다.
	 * @param existingStems 이 주차의 살아 있는 문항 본문 (중복 판정용)
	 */
	public record RoundInput(long jobId, String unitTitle, List<SourceSentence> sentences, List<String> existingStems,
							 int round) {
	}

	/** {@code leakTestAnswer}: 누출 시험에서 노트 없는 제자가 낸 답 (모름이면 null) — 검증 로그용 */
	public record CandidateOutcome(QuestionCandidate candidate, boolean passed, ValidationStage failedStage,
								   List<CandidateFailure> failures, String leakTestAnswer) {

		static CandidateOutcome passed(QuestionCandidate candidate, String leakTestAnswer) {
			return new CandidateOutcome(candidate, true, null, List.of(), leakTestAnswer);
		}

		static CandidateOutcome failed(QuestionCandidate candidate, ValidationStage stage,
									   List<CandidateFailure> failures, String leakTestAnswer) {
			return new CandidateOutcome(candidate, false, stage, failures, leakTestAnswer);
		}

	}

	public record RoundResult(int generatedCount, List<CandidateOutcome> outcomes) {

		public List<CandidateOutcome> passed() {
			return outcomes.stream().filter(CandidateOutcome::passed).toList();
		}

	}

	public RoundResult runRound(RoundInput input) {
		List<SourceSentence> window = SentenceWindows.slice(input.sentences(), properties.promptSentencesMaxChars(),
			input.round());
		List<QuestionCandidate> candidates = generate(input, window);

		// 2단계: 코드 확인. 번호는 주차 전체 기준이라 구간이 아닌 전체 문장으로 찾는다.
		Map<Integer, SourceSentence> sentencesByNo = input.sentences().stream()
			.collect(Collectors.toMap(SourceSentence::no, Function.identity(), (a, b) -> a));
		List<String> knownStems = new ArrayList<>(input.existingStems());
		List<CandidateOutcome> outcomes = new ArrayList<>();
		List<QuestionCandidate> toLeakTest = new ArrayList<>();
		for (QuestionCandidate candidate : candidates) {
			List<CandidateFailure> failures = EvidenceChecker.check(candidate, sentencesByNo, knownStems);
			if (failures.isEmpty()) {
				knownStems.add(candidate.stem());   // 같은 라운드 안의 중복도 막는다
				toLeakTest.add(candidate);
			} else {
				outcomes.add(CandidateOutcome.failed(candidate, ValidationStage.EVIDENCE_CHECK, failures, null));
			}
		}

		// 3단계: 누출 시험 (통과 후보를 한 번에, AI 1회)
		if (!toLeakTest.isEmpty()) {
			outcomes.addAll(leakTest(input.jobId(), toLeakTest));
		}
		return new RoundResult(candidates.size(), outcomes);
	}

	private List<QuestionCandidate> generate(RoundInput input, List<SourceSentence> window) {
		LlmRequest request = new LlmRequest(AiPurpose.QUESTION_GENERATE, PROMPT_NAME, PROMPT_VERSION,
			Map.of(
				"unit_title", input.unitTitle(),
				"batch_size", Integer.toString(properties.batchSize()),
				"existing_stems", formatStems(input.existingStems()),
				"sentences", formatSentences(window)),
			null, RELATED_TYPE, input.jobId());
		QuestionGenerateResponse response = llmClient.generateJson(request, QuestionGenerateResponse.class);
		return response == null ? List.of() : response.candidates();
	}

	private List<CandidateOutcome> leakTest(long jobId, List<QuestionCandidate> candidates) {
		StringBuilder questions = new StringBuilder();
		for (int i = 0; i < candidates.size(); i++) {
			questions.append('[').append(i + 1).append("] ").append(candidates.get(i).stem()).append('\n');
		}
		LlmRequest request = new LlmRequest(AiPurpose.LEAK_TEST, LEAK_PROMPT_NAME, LEAK_PROMPT_VERSION,
			Map.of("questions", questions.toString().stripTrailing()), null, RELATED_TYPE, jobId);
		SolveLlmResponse response = llmClient.generateJson(request, SolveLlmResponse.class);

		Map<Long, SolveLlmResponse.LlmAnswer> answersByNo = new HashMap<>();
		if (response != null) {
			for (SolveLlmResponse.LlmAnswer answer : response.answersOrEmpty()) {
				if (answer != null && answer.questionId() != null) {
					answersByNo.putIfAbsent(answer.questionId(), answer);
				}
			}
		}

		List<CandidateOutcome> outcomes = new ArrayList<>();
		for (int i = 0; i < candidates.size(); i++) {
			QuestionCandidate candidate = candidates.get(i);
			SolveLlmResponse.LlmAnswer answer = answersByNo.get((long) (i + 1));
			boolean unknown = answer == null || Boolean.TRUE.equals(answer.unknown()) || answer.answer() == null
				|| answer.answer().isBlank();
			if (unknown) {
				outcomes.add(CandidateOutcome.passed(candidate, null));
			} else if (isCorrect(candidate, answer.answer())) {
				outcomes.add(CandidateOutcome.failed(candidate, ValidationStage.LEAK_TEST,
					List.of(CandidateFailure.LEAKED), answer.answer()));
			} else {
				outcomes.add(CandidateOutcome.passed(candidate, answer.answer()));
			}
		}
		return outcomes;
	}

	/** 노트 없이 맞혔는가. 애매한 답(AMBIGUOUS)은 맞힌 것으로 보지 않는다. */
	private static boolean isCorrect(QuestionCandidate candidate, String answer) {
		CodeVerdict verdict = candidate.gradingMode() == GradingMode.ANSWER_LIST
			? CodeGrader.gradeAnswerList(answer, candidate.answers())
			: CodeGrader.gradeKeyElements(answer, candidate.keyElements(), null);
		return verdict == CodeVerdict.CORRECT;
	}

	private static String formatSentences(List<SourceSentence> sentences) {
		return sentences.stream().map(s -> "[" + s.no() + "] " + s.text()).collect(Collectors.joining("\n"));
	}

	private static String formatStems(List<String> stems) {
		if (stems.isEmpty()) {
			return "(없음)";
		}
		int from = Math.max(0, stems.size() - EXISTING_STEMS_IN_PROMPT);
		return stems.subList(from, stems.size()).stream().map(stem -> "- " + stem).collect(Collectors.joining("\n"));
	}

}
