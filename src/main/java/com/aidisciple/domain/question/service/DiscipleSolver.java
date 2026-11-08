package com.aidisciple.domain.question.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.LlmRequest;

/**
 * 제자가 노트만으로 문항을 푼다 (AI 1회). 문제 은행의 누출 시험(노트 없이)과 배틀 풀이 J-03(참가자 노트로)이 함께 쓴다.
 * DB 를 모른다. 후처리(api-spec 11-3 의 3단계)는 여기서 한다:
 * 제공하지 않은 노트 번호는 버리고, 노트 인용이 필수인데 인용이 없으면 UNKNOWN 으로 강제한다.
 */
@Component
@RequiredArgsConstructor
public class DiscipleSolver {

	static final String PROMPT_NAME = "battle-solve";
	static final String PROMPT_VERSION = "v1";

	private final LlmClient llmClient;

	/**
	 * @param requireNoteCitation true 면 노트를 인용하지 않은 답을 UNKNOWN 으로 바꾼다 (배틀).
	 *                            누출 시험은 노트가 비어 있으므로 false 로 부른다 — 그래야 노트 없이 맞힌 답을 잡을 수 있다.
	 */
	public record SolveInput(AiPurpose purpose, Long userId, String relatedType, Long relatedId,
							 List<SolveNote> notes, List<SolveQuestion> questions, boolean requireNoteCitation) {
	}

	public record SolveNote(long id, String content) {
	}

	public record SolveQuestion(long id, String stem) {
	}

	/** {@code forcedUnknown}: AI 는 답했지만 노트를 인용하지 않아 서버가 UNKNOWN 으로 바꿨다 (복기 근거용) */
	public record SolvedAnswer(long questionId, String answer, boolean unknown, List<Long> usedNoteIds,
							   boolean forcedUnknown) {

		static SolvedAnswer unknown(long questionId, boolean forced) {
			return new SolvedAnswer(questionId, "", true, List.of(), forced);
		}

	}

	/** 문항 순서대로 답을 돌려준다. AI 가 빠뜨린 문항은 UNKNOWN 이다. */
	public List<SolvedAnswer> solve(SolveInput input) {
		LlmRequest request = new LlmRequest(input.purpose(), PROMPT_NAME, PROMPT_VERSION,
			Map.of("notes", formatNotes(input.notes()), "questions", formatQuestions(input.questions())),
			input.userId(), input.relatedType(), input.relatedId());
		SolveLlmResponse response = llmClient.generateJson(request, SolveLlmResponse.class);

		Map<Long, SolveLlmResponse.LlmAnswer> byQuestion = new HashMap<>();
		if (response != null) {
			for (SolveLlmResponse.LlmAnswer answer : response.answersOrEmpty()) {
				if (answer != null && answer.questionId() != null) {
					byQuestion.putIfAbsent(answer.questionId(), answer);
				}
			}
		}
		Set<Long> givenNoteIds = input.notes().stream().map(SolveNote::id).collect(Collectors.toSet());
		return input.questions().stream()
			.map(question -> toSolved(question.id(), byQuestion.get(question.id()), givenNoteIds,
				input.requireNoteCitation()))
			.toList();
	}

	private static SolvedAnswer toSolved(long questionId, SolveLlmResponse.LlmAnswer raw, Set<Long> givenNoteIds,
										 boolean requireNoteCitation) {
		if (raw == null || Boolean.TRUE.equals(raw.unknown()) || raw.answer() == null || raw.answer().isBlank()) {
			return SolvedAnswer.unknown(questionId, false);
		}
		List<Long> usedNoteIds = raw.usedNoteIds() == null ? List.of() : raw.usedNoteIds().stream()
			.filter(Objects::nonNull)
			.filter(givenNoteIds::contains)
			.distinct()
			.toList();
		if (requireNoteCitation && usedNoteIds.isEmpty()) {
			return SolvedAnswer.unknown(questionId, true);
		}
		return new SolvedAnswer(questionId, raw.answer().strip(), false, usedNoteIds, false);
	}

	private static String formatNotes(List<SolveNote> notes) {
		if (notes.isEmpty()) {
			return "(없음)";
		}
		return notes.stream().map(note -> "[" + note.id() + "] " + note.content()).collect(Collectors.joining("\n"));
	}

	private static String formatQuestions(List<SolveQuestion> questions) {
		return questions.stream().map(q -> "[" + q.id() + "] " + q.stem()).collect(Collectors.joining("\n"));
	}

}
