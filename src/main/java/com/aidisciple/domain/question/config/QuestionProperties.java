package com.aidisciple.domain.question.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.aidisciple.domain.question.entity.QuestionStatus;

/** {@code app.question.*}. 값의 의미는 application.yml 주석. 테스트에서는 {@link #defaults()} 로 만든다. */
@ConfigurationProperties("app.question")
public record QuestionProperties(
	@DefaultValue("true") boolean allowUnjudged,
	@DefaultValue("10") int targetCount,
	@DefaultValue("5") int batchSize,
	@DefaultValue("3") int maxRounds,
	@DefaultValue("12000") int promptSentencesMaxChars
) {

	public static QuestionProperties defaults() {
		return new QuestionProperties(true, 10, 5, 3, 12000);
	}

	/** 배틀에 쓸 수 있는 문항 상태. UNJUDGED 는 설정이 켜졌을 때만 (api-spec 11-2) */
	public List<QuestionStatus> usableStatuses() {
		return allowUnjudged
			? List.of(QuestionStatus.VERIFIED, QuestionStatus.UNJUDGED)
			: List.of(QuestionStatus.VERIFIED);
	}

}
