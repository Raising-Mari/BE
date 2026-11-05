package com.aidisciple.domain.teaching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.teaching.*}. 노트 검증과 병합의 기준값이다. 값의 의미는 api-spec 11-1.
 * 순수 로직(검증기)이 이 레코드를 그대로 받으므로 테스트에서는 {@link #defaults()} 나 생성자로 직접 만든다.
 */
@ConfigurationProperties("app.teaching")
public record TeachingProperties(
	@DefaultValue("0.6") double minOverlap,
	@DefaultValue("10") int recentMessages,
	@DefaultValue("60") int maxNotesPerUnit,
	@DefaultValue("0.9") double mergeAutoThreshold,
	@DefaultValue("0.75") double mergeSuggestThreshold,
	@DefaultValue("6000") int promptNotesMaxChars
) {

	public static TeachingProperties defaults() {
		return new TeachingProperties(0.6, 10, 60, 0.9, 0.75, 6000);
	}

}
