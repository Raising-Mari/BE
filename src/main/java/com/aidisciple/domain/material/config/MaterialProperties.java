package com.aidisciple.domain.material.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.material.*}. 값의 의미는 application.yml 에 있다. 테스트에서는 {@link #defaults()} 나 생성자로 직접 만든다. */
@ConfigurationProperties("app.material")
public record MaterialProperties(
	@DefaultValue("100000") int maxTextChars,
	@DefaultValue("500000") int maxExtractedChars,
	@DefaultValue("500") int maxPages,
	@DefaultValue("30") int minTextChars
) {

	public static MaterialProperties defaults() {
		return new MaterialProperties(100_000, 500_000, 500, 30);
	}

}
