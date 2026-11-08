package com.aidisciple.domain.grading.logic;

import java.util.ArrayList;
import java.util.List;

import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.logic.CodeGrader;
import com.aidisciple.domain.question.logic.CodeVerdict;
import com.aidisciple.domain.question.logic.KeyElement;
import com.aidisciple.global.text.TextNormalizer;

/**
 * 코드로 하는 채점(api-spec 11-4 의 1~3): 모름 → 지시문 탐지 → 코드 채점. DB·AI 를 모른다.
 * 코드로 가를 수 없는 답은 {@link Result#ambiguous()} 로 돌려 LLM 판정(4단계)에 넘긴다 — 부분 정답은 그 단계에서만 나온다.
 */
public final class AnswerCodeGrader {

	private final InjectionDetector injectionDetector;

	public AnswerCodeGrader(InjectionDetector injectionDetector) {
		this.injectionDetector = injectionDetector;
	}

	/**
	 * @param answerText     제자의 답(모름이면 비어 있다)
	 * @param unknown        제자가 모른다고 했거나 서버가 모름으로 강제했다
	 * @param citedNoteTexts 제자가 "썼다"고 밝힌 노트의 내용
	 */
	public record Input(String answerText, boolean unknown, GradingQuestion question, List<String> citedNoteTexts) {
	}

	/**
	 * @param verdict 정해졌으면 그 판정, 코드로 못 가르면 null
	 * @param codeVerdict AMBIGUOUS 까지 포함한 코드 채점 결과. 모름·주입으로 끝난 답은 null
	 */
	public record Result(Verdict verdict, boolean ambiguous, CodeVerdict codeVerdict, List<String> injectionPatterns,
						 List<String> matchedElements, List<String> missingElements, List<String> elementsInNotes) {

		static Result decided(Verdict verdict, CodeVerdict codeVerdict, List<String> injectionPatterns, List<String> matched,
							  List<String> missing, List<String> inNotes) {
			return new Result(verdict, false, codeVerdict, injectionPatterns, matched, missing, inNotes);
		}

	}

	public Result grade(Input input) {
		if (input.unknown() || input.answerText() == null || input.answerText().isBlank()) {
			return Result.decided(Verdict.UNKNOWN, null, List.of(), List.of(), List.of(), List.of());
		}
		List<String> texts = new ArrayList<>(input.citedNoteTexts());
		texts.add(input.answerText());
		List<String> injected = injectionDetector.find(texts);
		if (!injected.isEmpty()) {
			return Result.decided(Verdict.ZEROED_INJECTION, null, injected, List.of(), List.of(), List.of());
		}

		GradingQuestion question = input.question();
		String notesText = String.join("\n", input.citedNoteTexts());
		if (question.gradingMode() == GradingMode.KEY_ELEMENTS) {
			return gradeKeyElements(input.answerText(), question.keyElements(), notesText);
		}
		return gradeAnswerList(input.answerText(), question.answers());
	}

	private static Result gradeAnswerList(String answer, List<String> expectedAnswers) {
		CodeVerdict verdict = CodeGrader.gradeAnswerList(answer, expectedAnswers);
		String compactAnswer = TextNormalizer.compact(answer);
		List<String> matched = expectedAnswers.stream()
			.filter(expected -> !TextNormalizer.compact(expected).isEmpty()
				&& compactAnswer.contains(TextNormalizer.compact(expected)))
			.toList();
		return of(verdict, List.of(), matched, List.of(), List.of());
	}

	private static Result gradeKeyElements(String answer, List<KeyElement> elements, String notesText) {
		CodeVerdict verdict = CodeGrader.gradeKeyElements(answer, elements, notesText);
		String compactAnswer = TextNormalizer.compact(answer);
		String compactNotes = TextNormalizer.compact(notesText);
		List<String> matched = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		List<String> inNotes = new ArrayList<>();
		for (KeyElement element : elements) {
			boolean inNote = element.foundIn(compactNotes);
			if (inNote) {
				inNotes.add(element.label());
			}
			// 노트에도 있어야 센다 — 노트에 없는데 맞힌 것은 AI 지식 누출로 보고 인정하지 않는다
			if (element.foundIn(compactAnswer) && inNote) {
				matched.add(element.label());
			} else {
				missing.add(element.label());
			}
		}
		return of(verdict, List.of(), matched, missing, inNotes);
	}

	private static Result of(CodeVerdict verdict, List<String> injected, List<String> matched, List<String> missing,
							 List<String> inNotes) {
		return switch (verdict) {
			case CORRECT -> Result.decided(Verdict.CORRECT, verdict, injected, matched, missing, inNotes);
			case WRONG -> Result.decided(Verdict.WRONG, verdict, injected, matched, missing, inNotes);
			case AMBIGUOUS -> new Result(null, true, verdict, injected, matched, missing, inNotes);
		};
	}

}
