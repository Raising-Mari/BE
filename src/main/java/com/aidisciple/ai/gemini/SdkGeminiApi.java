package com.aidisciple.ai.gemini;

import java.io.IOException;
import java.io.InterruptedIOException;

import com.aidisciple.ai.AiProperties;
import com.aidisciple.ai.gemini.GeminiApiException.Kind;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;

/**
 * Google GenAI Java SDK 로 Gemini 를 한 번 부른다. SDK 코드는 이 클래스 밖으로 나가지 않는다. (ai/gemini/AGENTS.md)
 * 재시도·동시 호출 제한·JSON 파싱·기록은 GeminiLlmClient 가 맡는다.
 * 예외 메시지에는 HTTP 상태만 담는다. 프롬프트·응답 원문과 API 키는 담지 않는다.
 */
public class SdkGeminiApi implements GeminiApi, AutoCloseable {

	private final AiProperties.Gemini config;
	private final Client client;   // 키가 없으면 null

	public SdkGeminiApi(AiProperties.Gemini config) {
		this.config = config;
		this.client = hasText(config.apiKey())
			? Client.builder()
			.apiKey(config.apiKey())
			.httpOptions(HttpOptions.builder()
				.timeout(config.timeoutSeconds() * 1000)
				.build())
			.build()
			: null;
	}

	@Override
	public boolean isConfigured() {
		return client != null;
	}

	@Override
	public GeminiRawResponse generate(String prompt, Class<?> responseType) {
		GenerateContentConfig request = GenerateContentConfig.builder()
			.responseMimeType("application/json")
			.maxOutputTokens(config.maxOutputTokens())
			.build();
		try {
			GenerateContentResponse response = client.models.generateContent(config.model(), prompt, request);
			String text = response.text();
			Integer input = response.usageMetadata()
				.flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(null);
			Integer output = response.usageMetadata()
				.flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).orElse(null);
			return new GeminiRawResponse(text == null ? "" : text, input, output);
		} catch (ApiException e) {
			throw new GeminiApiException(classify(e.code()), "Gemini HTTP " + e.code(), e);
		} catch (RuntimeException e) {
			if (hasCause(e, InterruptedIOException.class)) {      // 소켓 타임아웃 포함
				throw new GeminiApiException(Kind.TIMEOUT, "Gemini 응답 시간 초과", e);
			}
			if (hasCause(e, IOException.class)) {
				throw new GeminiApiException(Kind.SERVER_ERROR, "Gemini 통신 오류", e);
			}
			throw e;
		}
	}

	private static Kind classify(int status) {
		if (status == 429) {
			return Kind.RATE_LIMITED;
		}
		if (status == 408 || status == 504) {
			return Kind.TIMEOUT;
		}
		if (status >= 500) {
			return Kind.SERVER_ERROR;
		}
		return Kind.CLIENT_ERROR;
	}

	private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (type.isInstance(t)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	@Override
	public void close() {
		if (client != null) {
			client.close();
		}
	}
}
