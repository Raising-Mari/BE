package com.aidisciple.domain.grading.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.scoring.*}. 점수 공식의 숫자는 여기 한 곳에만 둔다(api-spec 11-4의 5·6). 초기값이라 시연 세트로 조정한다.
 * 테스트에서는 {@link #defaults()} 로 만든다.
 *
 * @param correct          정답 점수
 * @param partial          부분 정답 점수
 * @param wrongAnswerList  정답 목록형 오답의 감점(양수로 쓴다)
 * @param wrongKeyElements 핵심 요소형 오답의 감점(양수로 쓴다)
 * @param copyLowRatio     복붙 비율이 이 값 이하면 보정하지 않는다
 * @param copyHighRatio    복붙 비율이 이 값 이상이면 계수가 최소값이다
 * @param copyMinFactor    복붙 보정 계수의 최소값(정답·부분 정답 점수에 곱한다)
 * @param copyNgram        복붙 비율을 재는 글자 n-gram 길이
 */
@ConfigurationProperties("app.scoring")
public record ScoringProperties(
	@DefaultValue("1") BigDecimal correct,
	@DefaultValue("0.5") BigDecimal partial,
	@DefaultValue("0.5") BigDecimal wrongAnswerList,
	@DefaultValue("0.3") BigDecimal wrongKeyElements,
	@DefaultValue("0.5") double copyLowRatio,
	@DefaultValue("0.9") double copyHighRatio,
	@DefaultValue("0.5") BigDecimal copyMinFactor,
	@DefaultValue("8") int copyNgram
) {

	public static ScoringProperties defaults() {
		return new ScoringProperties(new BigDecimal("1"), new BigDecimal("0.5"), new BigDecimal("0.5"),
			new BigDecimal("0.3"), 0.5, 0.9, new BigDecimal("0.5"), 8);
	}

}
