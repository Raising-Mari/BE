package com.aidisciple.domain.question.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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

}
