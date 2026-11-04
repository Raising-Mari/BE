package com.aidisciple.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.ai.* 설정. 모델 이름·타임아웃 등은 코드에 하드코딩하지 않는다. (ai/gemini/AGENTS.md)
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
	int maxConcurrency,      // 전역 AI 동시 호출 수 (backend-plan 6-2: 2)
	Gemini gemini
) {

	public record Gemini(
		String apiKey,       // GEMINI_API_KEY. 비어 있으면 호출 때 AI_UNAVAILABLE
		String model,
		int timeoutSeconds,
		int maxOutputTokens,
		int maxAttempts,     // 일시 오류(5xx·타임아웃) 재시도 포함 총 시도 수
		long backoffMillis   // 첫 재시도 대기 시간. 이후 2배씩
	) {
	}
}
