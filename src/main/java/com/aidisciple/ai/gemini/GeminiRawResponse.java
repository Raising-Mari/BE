package com.aidisciple.ai.gemini;

/** Gemini 응답 원문(JSON 텍스트)과 토큰 수. 원문은 로그에 남기지 않는다. */
public record GeminiRawResponse(String text, Integer inputTokens, Integer outputTokens) {
}
