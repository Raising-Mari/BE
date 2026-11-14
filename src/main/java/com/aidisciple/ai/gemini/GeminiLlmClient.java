package com.aidisciple.ai.gemini;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiProperties;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.LlmRequest;
import com.aidisciple.ai.log.AiCallLogger;
import com.aidisciple.ai.log.AiCallRecord;
import com.aidisciple.ai.log.AiProvider;
import com.aidisciple.ai.prompt.PromptLoader;
import com.aidisciple.global.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini 로 LlmClient 를 구현한다. 모든 AI 호출이 여기 한 곳을 지난다. (ai/AGENTS.md)
 * - 동시 호출 수 제한, 일시 오류는 지수 백오프 재시도, 429 는 즉시 반복하지 않음
 * - JSON 형식이 깨지면 1회만 다시 호출하고, 그래도 깨지면 AI_BAD_RESPONSE
 * - 호출 1회마다 ai_call_logs 1행. 프롬프트·응답 원문과 API 키는 로그에 남기지 않는다
 */
public class GeminiLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
	private static final int MAX_PARSE_ATTEMPTS = 2;

	private final GeminiApi api;
	private final PromptLoader promptLoader;
	private final AiCallLogger callLogger;
	private final JsonMapper jsonMapper;
	private final AiProperties.Gemini config;
	private final Semaphore permits;
	private final Sleeper sleeper;

	public GeminiLlmClient(GeminiApi api, PromptLoader promptLoader, AiCallLogger callLogger,
						   JsonMapper jsonMapper, AiProperties properties, Sleeper sleeper) {
		this.api = api;
		this.promptLoader = promptLoader;
		this.callLogger = callLogger;
		this.jsonMapper = jsonMapper;
		this.config = properties.gemini();
		this.permits = new Semaphore(Math.max(1, properties.maxConcurrency()), true);
		this.sleeper = sleeper;
	}

	@Override
	public <T> T generateJson(LlmRequest request, Class<T> responseType) {
		if (!api.isConfigured()) {
			log.warn("GEMINI_API_KEY 가 비어 있어 AI 를 호출할 수 없습니다. purpose={}", request.purpose());
			throw new AiException(ErrorCode.AI_UNAVAILABLE);
		}
		String prompt = promptLoader.load(request.promptName(), request.promptVersion())
			.render(request.variables());

		for (int parseAttempt = 1; ; parseAttempt++) {
			Attempt attempt = callWithRetry(request, prompt, responseType);
			try {
				T result = jsonMapper.readValue(attempt.raw().text(), responseType);
				callLogger.record(record(request, attempt, true, null));
				return result;
			} catch (JacksonException e) {
				callLogger.record(record(request, attempt, false, "BAD_RESPONSE"));
				log.warn("AI 응답 JSON 형식 오류 ({}/{}): purpose={}, prompt={}.{}",
					parseAttempt, MAX_PARSE_ATTEMPTS,
					request.purpose(), request.promptName(), request.promptVersion());
				if (parseAttempt >= MAX_PARSE_ATTEMPTS) {
					throw new AiException(ErrorCode.AI_BAD_RESPONSE, e);
				}
			}
		}
	}

	/** 일시 오류는 지수 백오프로 재시도한다. 실패한 호출도 1행씩 기록한다. */
	private Attempt callWithRetry(LlmRequest request, String prompt, Class<?> responseType) {
		int maxAttempts = Math.max(1, config.maxAttempts());
		for (int attempt = 1; ; attempt++) {
			long started = System.nanoTime();
			try {
				GeminiRawResponse raw = callWithPermit(prompt, responseType);
				return new Attempt(raw, elapsedMillis(started));
			} catch (GeminiApiException e) {
				callLogger.record(AiCallRecord.failure(request, AiProvider.GEMINI, config.model(),
					elapsedMillis(started), e.kind().name()));
				if (!e.retryable() || attempt >= maxAttempts) {
					throw toAiException(e);
				}
				long backoff = config.backoffMillis() * (1L << (attempt - 1));
				log.warn("AI 호출 실패, {}ms 뒤 재시도 ({}/{}): purpose={}, kind={}",
					backoff, attempt, maxAttempts, request.purpose(), e.kind());
				sleeper.sleep(backoff);
			}
		}
	}

	/** 전역 동시 호출 수를 넘지 않게 한다. 자리가 안 나면 기다리다가 AI_UNAVAILABLE. */
	private GeminiRawResponse callWithPermit(String prompt, Class<?> responseType) {
		boolean acquired;
		try {
			acquired = permits.tryAcquire(config.timeoutSeconds(), TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiException(ErrorCode.AI_UNAVAILABLE, e);
		}
		if (!acquired) {
			throw new AiException(ErrorCode.AI_UNAVAILABLE);
		}
		try {
			return api.generate(prompt, responseType);
		} finally {
			permits.release();
		}
	}

	private static AiException toAiException(GeminiApiException e) {
		ErrorCode code = switch (e.kind()) {
			case RATE_LIMITED -> ErrorCode.AI_RATE_LIMITED;
			case TIMEOUT -> ErrorCode.AI_TIMEOUT;
			case SERVER_ERROR, CLIENT_ERROR -> ErrorCode.AI_UNAVAILABLE;
		};
		return new AiException(code, e);
	}

	private AiCallRecord record(LlmRequest r, Attempt a, boolean success, String errorCode) {
		return new AiCallRecord(r.purpose(), AiProvider.GEMINI, config.model(),
			r.promptName(), r.promptVersion(),
			a.raw().inputTokens(), a.raw().outputTokens(), a.latencyMillis(),
			success, errorCode, r.userId(), r.relatedType(), r.relatedId());
	}

	private static int elapsedMillis(long startedNanos) {
		return (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
	}

	private record Attempt(GeminiRawResponse raw, int latencyMillis) {
	}
}
