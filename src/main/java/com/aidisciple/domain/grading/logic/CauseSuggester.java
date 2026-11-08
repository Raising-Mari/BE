package com.aidisciple.domain.grading.logic;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.logic.KeyElement;
import com.aidisciple.domain.review.entity.ReviewCause;
import com.aidisciple.global.text.CharNgrams;
import com.aidisciple.global.text.NegationDetector;
import com.aidisciple.global.text.TextNormalizer;

/**
 * 오답 원인 제안(api-spec 11-4 의 7). WRONG·PARTIAL·UNKNOWN·UNJUDGED 일 때만 부른다. <b>제안일 뿐이고 최종 원인은 학생이 고른다</b>(R-02).
 * 그 참가자의 준비 완료 시점 노트(스냅샷) 기준이다. DB·AI 를 모른다.
 * <ol>
 *   <li>근거 문장과 겹치는 노트가 하나도 없다 → 학생이 한 말과 근거를 비교해, 겹치는 말이 있으면 {@code NOT_IN_NOTES}(말했는데 노트로 안 남음),
 *       없으면 {@code NOT_TAUGHT}(가르치지 않음)</li>
 *   <li>겹치는 노트는 있으나 핵심 요소가 빠졌거나 부정 표현이 근거와 반대 → {@code NOTE_WRONG}</li>
 *   <li>핵심 요소가 노트에 모두 있는데도 틀림·모름 → {@code NOT_APPLIED}(AI 쪽 문제일 수 있음)</li>
 * </ol>
 * {@code OTHER} 는 제안하지 않는다 — 학생만 고른다.
 */
public final class CauseSuggester {

	private static final int NGRAM = 3;

	private final double overlapThreshold;

	public CauseSuggester(double overlapThreshold) {
		this.overlapThreshold = overlapThreshold;
	}

	/**
	 * @param studentMessages 이 제자·이 주차에서 학생이 보낸 메시지. 겹치는 노트가 없을 때만 필요하므로 그때 처음 읽는다.
	 */
	public ReviewCause suggest(GradingQuestion question, List<String> snapshotNotes, Supplier<List<String>> studentMessages) {
		String evidence = question.evidenceText();
		List<String> overlapping = snapshotNotes.stream().filter(note -> overlaps(evidence, note)).toList();
		if (overlapping.isEmpty()) {
			boolean saidInChat = studentMessages.get().stream().anyMatch(message -> overlaps(evidence, message));
			return saidInChat ? ReviewCause.NOT_IN_NOTES : ReviewCause.NOT_TAUGHT;
		}
		boolean negationFlipped = overlapping.stream().noneMatch(note -> NegationDetector.sameParity(evidence, note));
		if (negationFlipped || !allKeyElementsInNotes(question, snapshotNotes)) {
			return ReviewCause.NOTE_WRONG;
		}
		return ReviewCause.NOT_APPLIED;
	}

	/** 두 글의 3-gram 겹침(겹친 개수 / 더 작은 집합의 크기)이 기준 이상인가 */
	boolean overlaps(String a, String b) {
		Set<String> gramsA = CharNgrams.of(a == null ? "" : a, NGRAM);
		Set<String> gramsB = CharNgrams.of(b == null ? "" : b, NGRAM);
		if (gramsA.isEmpty() || gramsB.isEmpty()) {
			return false;
		}
		long shared = gramsA.stream().filter(gramsB::contains).count();
		return (double) shared / Math.min(gramsA.size(), gramsB.size()) >= overlapThreshold;
	}

	/** 핵심 요소형은 모든 요소가, 정답 목록형은 정답 표현 하나가 노트에 있어야 "갖춰졌다"고 본다. */
	private static boolean allKeyElementsInNotes(GradingQuestion question, List<String> notes) {
		String compactNotes = TextNormalizer.compact(String.join("\n", notes));
		if (question.gradingMode() == GradingMode.KEY_ELEMENTS) {
			List<KeyElement> elements = question.keyElements();
			return !elements.isEmpty() && elements.stream().allMatch(element -> element.foundIn(compactNotes));
		}
		return question.answers().stream()
			.map(TextNormalizer::compact)
			.anyMatch(expected -> !expected.isEmpty() && compactNotes.contains(expected));
	}

}
