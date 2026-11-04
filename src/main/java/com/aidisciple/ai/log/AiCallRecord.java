package com.aidisciple.ai.log;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmRequest;

/**
 * AI 호출 1회의 기록 값. 프롬프트·응답 원문과 학생 입력은 담지 않는다.
 */
public record AiCallRecord(
	AiPurpose purpose,
	AiProvider provider,
	String model,
	String promptName,
	String promptVersion,
	Integer inputTokens,
	Integer outputTokens,
	int latencyMs,
	boolean success,
	String errorCode,
	Long userId,
	String relatedType,
	Long relatedId
) {

	private static final int ERROR_CODE_MAX = 40;

	public static AiCallRecord success(LlmRequest request, AiProvider provider, String model,
									   Integer inputTokens, Integer outputTokens, int latencyMs) {
		return new AiCallRecord(request.purpose(), provider, model,
			request.promptName(), request.promptVersion(),
			inputTokens, outputTokens, latencyMs, true, null,
			request.userId(), request.relatedType(), request.relatedId());
	}

	public static AiCallRecord failure(LlmRequest request, AiProvider provider, String model,
									   int latencyMs, String errorCode) {
		return new AiCallRecord(request.purpose(), provider, model,
			request.promptName(), request.promptVersion(),
			null, null, latencyMs, false, truncate(errorCode),
			request.userId(), request.relatedType(), request.relatedId());
	}

	private static String truncate(String value) {
		if (value == null || value.length() <= ERROR_CODE_MAX) {
			return value;
		}
		return value.substring(0, ERROR_CODE_MAX);
	}
}
