package com.aidisciple.domain.grading.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.grading.*}. 테스트에서는 {@link #defaults()} 로 만든다.
 *
 * @param injectionPatterns    채점자를 겨냥한 지시문 패턴. 답이나 사용한 노트에 있으면 그 문항은 0점이다. 비워 두면 기본 목록을 쓴다.
 * @param llmBatchSize         애매한 답을 AI 한 번에 판정시키는 최대 개수. 넘으면 나눠서 부른다.
 * @param llmMaxAttempts       AI 판정 호출의 최대 시도 횟수. 끝내 실패하면 그 답들은 UNJUDGED(0점)로 둔다.
 * @param llmRetryBackoff      재시도 전 첫 대기. 시도마다 2배로 늘린다.
 * @param causeOverlapThreshold 원인 제안에서 "근거 문장과 겹친다"고 보는 3-gram 겹침 비율의 하한
 */
@ConfigurationProperties("app.grading")
public record GradingProperties(
	List<String> injectionPatterns,
	@DefaultValue("12") int llmBatchSize,
	@DefaultValue("2") int llmMaxAttempts,
	@DefaultValue("1s") Duration llmRetryBackoff,
	@DefaultValue("0.25") double causeOverlapThreshold
) {

	/** api-spec 11-4 가 예로 든 패턴. 설정이 비어 있어도 탐지가 꺼지지 않게 한다. */
	static final List<String> DEFAULT_INJECTION_PATTERNS = List.of("정답으로 처리", "정답으로 간주", "정답 처리", "만점", "채점",
		"ignore", "system prompt", "위 지시", "이전 지시");

	public GradingProperties {
		injectionPatterns = injectionPatterns == null || injectionPatterns.isEmpty()
			? DEFAULT_INJECTION_PATTERNS : List.copyOf(injectionPatterns);
	}

	public static GradingProperties defaults() {
		return new GradingProperties(null, 12, 2, Duration.ofSeconds(1), 0.25);
	}

}
