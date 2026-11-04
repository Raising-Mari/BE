package com.aidisciple.ai.gemini;

import com.aidisciple.ai.AiProperties;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.log.AiCallLogger;
import com.aidisciple.ai.prompt.PromptLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * LlmClient 를 Gemini 구현으로 등록한다. domain/ 은 LlmClient 만 주입받는다. (ai/AGENTS.md)
 * API 키가 비어 있어도 서버는 뜨고, AI 를 부르는 순간 AI_UNAVAILABLE 이 난다.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class GeminiConfig {

	@Bean
	public SdkGeminiApi geminiApi(AiProperties properties) {
		return new SdkGeminiApi(properties.gemini());
	}

	@Bean
	public LlmClient llmClient(GeminiApi geminiApi, PromptLoader promptLoader, AiCallLogger callLogger,
							   JsonMapper jsonMapper, AiProperties properties) {
		return new GeminiLlmClient(geminiApi, promptLoader, callLogger, jsonMapper, properties, Sleeper.REAL);
	}
}
