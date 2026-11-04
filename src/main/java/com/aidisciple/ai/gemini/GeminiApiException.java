package com.aidisciple.ai.gemini;

/**
 * Gemini 호출 실패를 종류별로 분류한 예외. ai/gemini 밖으로는 나가지 않고, GeminiLlmClient 가 AiException 으로 바꾼다.
 */
public class GeminiApiException extends RuntimeException {

	public enum Kind {
		RATE_LIMITED,   // 429 — 즉시 반복하지 않는다
		TIMEOUT,        // 시간 초과 — 재시도
		SERVER_ERROR,   // 5xx·네트워크 — 재시도
		CLIENT_ERROR    // 그 밖의 4xx (키·요청 오류) — 재시도해도 소용없다
	}

	private final Kind kind;

	public GeminiApiException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public GeminiApiException(Kind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public boolean retryable() {
		return kind == Kind.TIMEOUT || kind == Kind.SERVER_ERROR;
	}
}
