package com.aidisciple.domain.question.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.global.text.CharNgrams;
import com.aidisciple.global.text.TextNormalizer;

/**
 * 문제 은행 2단계 "코드 확인" (api-spec 11-2). AI 를 부르지 않는다.
 * 순서: 형식 → 근거 인용이 실제 자료에 있는가 → 정답이 근거 문장에서 성립하는가 → 기존 문항과 중복인가.
 * 형식이 틀리면 뒤 검사는 하지 않는다.
 */
public final class EvidenceChecker {

	static final double QUOTE_SIMILARITY = 0.9;
	static final double DUPLICATE_SIMILARITY = 0.8;
	static final double KEY_ELEMENTS_IN_EVIDENCE = 0.8;
	static final int MIN_STEM_LENGTH = 5;
	static final int MAX_STEM_LENGTH = 500;
	private static final int NGRAM = 3;

	private EvidenceChecker() {
	}

	/**
	 * @param sentencesByNo 프롬프트 번호 → 문장
	 * @param existingStems 이 주차의 기존 문항과, 같은 작업에서 먼저 통과한 후보의 문항 본문
	 * @return 탈락 이유 (비어 있으면 통과)
	 */
	public static List<CandidateFailure> check(QuestionCandidate candidate, Map<Integer, SourceSentence> sentencesByNo,
											   List<String> existingStems) {
		List<CandidateFailure> failures = new ArrayList<>();
		if (!isWellFormed(candidate)) {
			failures.add(CandidateFailure.INVALID_FORMAT);
			return failures;
		}

		List<SourceSentence> cited = candidate.evidenceSentenceNos().stream()
			.distinct()
			.map(sentencesByNo::get)
			.toList();
		boolean allCitedExist = !cited.isEmpty() && cited.stream().allMatch(Objects::nonNull);
		if (!allCitedExist || !quoteFound(candidate.evidenceQuote(), cited)) {
			failures.add(CandidateFailure.EVIDENCE_NOT_FOUND);
		} else if (!answerHoldsIn(candidate, cited)) {
			failures.add(CandidateFailure.ANSWER_NOT_IN_EVIDENCE);
		}

		boolean duplicate = existingStems.stream()
			.anyMatch(stem -> CharNgrams.similarity(candidate.stem(), stem, NGRAM) >= DUPLICATE_SIMILARITY);
		if (duplicate) {
			failures.add(CandidateFailure.DUPLICATE);
		}
		return failures;
	}

	static boolean isWellFormed(QuestionCandidate candidate) {
		if (candidate.gradingMode() == null || isBlank(candidate.referenceAnswer())
			|| isBlank(candidate.evidenceQuote()) || candidate.stem() == null) {
			return false;
		}
		int stemLength = candidate.stem().strip().length();
		if (stemLength < MIN_STEM_LENGTH || stemLength > MAX_STEM_LENGTH) {
			return false;
		}
		return switch (candidate.gradingMode()) {
			case ANSWER_LIST -> candidate.answers().stream().anyMatch(answer -> !isBlank(answer));
			case KEY_ELEMENTS -> candidate.keyElements().stream().anyMatch(element -> !isBlank(element.label()));
		};
	}

	/** 인용이 인용한 문장들 안에 그대로 있거나(정규화 후), 어느 한 문장과 거의 같으면(≥0.9) 찾은 것으로 본다. */
	private static boolean quoteFound(String quote, List<SourceSentence> cited) {
		String joined = cited.stream().map(SourceSentence::text).collect(Collectors.joining(" "));
		String compactQuote = TextNormalizer.compact(quote);
		if (!compactQuote.isEmpty() && TextNormalizer.compact(joined).contains(compactQuote)) {
			return true;
		}
		return cited.stream()
			.anyMatch(sentence -> CharNgrams.similarity(quote, sentence.text(), NGRAM) >= QUOTE_SIMILARITY);
	}

	/** 정답 목록형은 정답 표현 하나가 근거 문장에 있어야 하고, 핵심 요소형은 요소의 80% 이상이 근거 문장에 있어야 한다. */
	private static boolean answerHoldsIn(QuestionCandidate candidate, List<SourceSentence> cited) {
		String evidence = TextNormalizer.compact(
			cited.stream().map(SourceSentence::text).collect(Collectors.joining(" ")));
		if (candidate.gradingMode() == GradingMode.ANSWER_LIST) {
			return candidate.answers().stream()
				.map(TextNormalizer::compact)
				.anyMatch(answer -> !answer.isEmpty() && evidence.contains(answer));
		}
		long found = candidate.keyElements().stream().filter(element -> element.foundIn(evidence)).count();
		return (double) found / candidate.keyElements().size() >= KEY_ELEMENTS_IN_EVIDENCE;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

}
