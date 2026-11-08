package com.aidisciple.domain.grading.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.LlmRequest;
import com.aidisciple.domain.battle.entity.Verdict;
import com.aidisciple.domain.grading.config.GradingProperties;
import com.aidisciple.domain.question.dto.GradingQuestion;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.logic.KeyElement;

/**
 * 코드로 가르지 못한 애매한 답만 AI 에 판정시킨다(api-spec 11-4 의 4). <b>트랜잭션 밖에서만</b> 부른다.
 * 한 번에 {@code llmBatchSize} 개까지 모아 부르고, 호출이 실패하면 몇 번 다시 시도한다. 끝내 실패하거나 응답이 형식에서 어긋나면
 * 그 답은 {@link Verdict#UNJUDGED}(0점, 감점 없음, 관리자 확인 대상)로 두고 점수에 임의로 반영하지 않는다 — 이 클래스는 예외를 던지지 않는다.
 * 부분 정답은 이 단계에서만 나온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeBatchJudge {

	static final String PROMPT_NAME = "grade-batch";
	static final String PROMPT_VERSION = "v1";
	static final String RELATED_TYPE = "BATTLE";
	static final int REASON_MAX = 100;
	static final String REASON_UNAVAILABLE = "AI 판정을 받지 못함";
	static final String REASON_BAD_FORMAT = "AI 판정 형식 오류";

	private static final Set<Verdict> ALLOWED = Set.of(Verdict.CORRECT, Verdict.PARTIAL, Verdict.WRONG);

	private final LlmClient llmClient;
	private final GradingProperties properties;

	/** 판정할 답 하나. {@code answerId} 가 응답과 이어 주는 번호다(같은 문항을 두 참가자가 푼 답을 구분한다). */
	public record Item(long answerId, GradingQuestion question, String answerText) {
	}

	/** {@code verdict} 는 CORRECT·PARTIAL·WRONG 이거나 판정하지 못한 UNJUDGED 다. */
	public record Judgment(Verdict verdict, String reason) {
	}

	/** 모든 항목에 판정이 하나씩 있다. */
	public Map<Long, Judgment> judge(long battleId, List<Item> items) {
		Map<Long, Judgment> result = new HashMap<>();
		int batchSize = Math.max(1, properties.llmBatchSize());
		for (int from = 0; from < items.size(); from += batchSize) {
			List<Item> chunk = items.subList(from, Math.min(items.size(), from + batchSize));
			result.putAll(judgeChunk(battleId, chunk));
		}
		return result;
	}

	private Map<Long, Judgment> judgeChunk(long battleId, List<Item> chunk) {
		GradeBatchResponse response = callWithRetry(battleId, chunk);
		if (response == null) {
			return unjudged(chunk, REASON_UNAVAILABLE);
		}
		Map<Long, GradeBatchResponse.LlmResult> byAnswer = new HashMap<>();
		for (GradeBatchResponse.LlmResult llmResult : response.resultsOrEmpty()) {
			if (llmResult != null && llmResult.answerId() != null) {
				byAnswer.putIfAbsent(llmResult.answerId(), llmResult);
			}
		}
		Map<Long, Judgment> result = new HashMap<>();
		for (Item item : chunk) {
			result.put(item.answerId(), toJudgment(byAnswer.get(item.answerId())));
		}
		return result;
	}

	private GradeBatchResponse callWithRetry(long battleId, List<Item> chunk) {
		LlmRequest request = new LlmRequest(AiPurpose.GRADE_BATCH, PROMPT_NAME, PROMPT_VERSION,
			Map.of("items", formatItems(chunk)), null, RELATED_TYPE, battleId);
		int maxAttempts = Math.max(1, properties.llmMaxAttempts());
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return llmClient.generateJson(request, GradeBatchResponse.class);
			} catch (AiException e) {
				log.warn("채점 AI 호출 실패: battleId={}, attempt={}, code={}", battleId, attempt, e.getErrorCode());
				if (attempt < maxAttempts && !backoff(attempt)) {
					return null;
				}
			}
		}
		return null;
	}

	/** 시도마다 대기를 2배로 늘린다. 인터럽트(서버 종료)면 더 기다리지 않고 false. */
	private boolean backoff(int attemptsSoFar) {
		long millis = properties.llmRetryBackoff().toMillis() << (attemptsSoFar - 1);
		if (millis <= 0) {
			return true;
		}
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** 모르는 번호·허용되지 않은 판정·빠진 항목은 모두 UNJUDGED 다. */
	private static Judgment toJudgment(GradeBatchResponse.LlmResult llmResult) {
		if (llmResult == null || llmResult.verdict() == null) {
			return new Judgment(Verdict.UNJUDGED, REASON_BAD_FORMAT);
		}
		Verdict verdict;
		try {
			verdict = Verdict.valueOf(llmResult.verdict().strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return new Judgment(Verdict.UNJUDGED, REASON_BAD_FORMAT);
		}
		if (!ALLOWED.contains(verdict)) {
			return new Judgment(Verdict.UNJUDGED, REASON_BAD_FORMAT);
		}
		return new Judgment(verdict, cleanReason(llmResult.reason()));
	}

	private static Map<Long, Judgment> unjudged(List<Item> chunk, String reason) {
		return chunk.stream().collect(Collectors.toMap(Item::answerId, item -> new Judgment(Verdict.UNJUDGED, reason)));
	}

	private static String cleanReason(String reason) {
		if (reason == null) {
			return "";
		}
		String oneLine = reason.replaceAll("\\p{Cntrl}+", " ").strip();
		return oneLine.length() <= REASON_MAX ? oneLine : oneLine.substring(0, REASON_MAX);
	}

	/**
	 * 프롬프트에 넣는 항목 목록. 제자의 답은 데이터이므로 줄바꿈을 없애 "[번호]" 줄을 흉내 내지 못하게 한다.
	 * (&lt; &gt; 는 프롬프트 변수를 채울 때 PromptTemplate 이 바꾼다.)
	 */
	static String formatItems(List<Item> items) {
		return items.stream().map(GradeBatchJudge::formatItem).collect(Collectors.joining("\n\n"));
	}

	private static String formatItem(Item item) {
		GradingQuestion question = item.question();
		StringBuilder text = new StringBuilder();
		text.append('[').append(item.answerId()).append("]\n");
		text.append("문항: ").append(oneLine(question.stem())).append('\n');
		text.append("모범 답: ").append(oneLine(question.referenceAnswer())).append('\n');
		if (question.gradingMode() == GradingMode.KEY_ELEMENTS) {
			text.append("핵심 요소: ").append(question.keyElements().stream().map(GradeBatchJudge::elementText)
				.collect(Collectors.joining(", "))).append('\n');
		} else {
			text.append("정답 표현: ").append(String.join(" / ", question.answers().stream().map(GradeBatchJudge::oneLine).toList()))
				.append('\n');
		}
		text.append("제자의 답: ").append(oneLine(item.answerText()));
		return text.toString();
	}

	private static String elementText(KeyElement element) {
		if (element.aliases().isEmpty()) {
			return oneLine(element.label());
		}
		return oneLine(element.label()) + "(" + String.join("·", element.aliases().stream().map(GradeBatchJudge::oneLine).toList()) + ")";
	}

	private static String oneLine(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").strip();
	}

}
