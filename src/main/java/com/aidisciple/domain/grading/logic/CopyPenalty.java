package com.aidisciple.domain.grading.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aidisciple.domain.grading.config.ScoringProperties;
import com.aidisciple.global.text.CharNgrams;

/**
 * 복붙 보정(api-spec 11-4 의 6). 답과 인용한 노트의 글자 n-gram 이 주차 자료에 얼마나 들어 있는지(포함률)를 재고,
 * 높을수록 정답 점수에 곱하는 계수를 낮춘다. 계수는 {@code lowRatio} 이하면 1.0, {@code highRatio} 이상이면 최소값, 사이는 선형이다.
 * <p>
 * 짧은 답(n 글자 미만)은 n-gram 이 없어 비율이 0 이다 — "후입선출" 같은 짧은 정답은 자료에 그대로 있어도 복붙이 아니다.
 */
public final class CopyPenalty {

	private final ScoringProperties properties;

	public CopyPenalty(ScoringProperties properties) {
		this.properties = properties;
	}

	/** 답과 인용한 노트의 포함률. {@code combined} 는 둘 중 큰 값이다. */
	public record Ratios(double answer, double notes, double combined) {
	}

	/** 주차에 연결된 자료 전체의 문장에서 n-gram 집합을 만든다. 문장마다 따로 만들어 문장 경계를 넘는 조각이 생기지 않게 한다. */
	public Set<String> materialGrams(List<String> materialSentences) {
		Set<String> grams = new HashSet<>();
		for (String sentence : materialSentences) {
			grams.addAll(CharNgrams.of(sentence, properties.copyNgram()));
		}
		return grams;
	}

	public Ratios ratios(Set<String> materialGrams, String answer, List<String> citedNotes) {
		double answerRatio = CharNgrams.containment(CharNgrams.of(answer == null ? "" : answer, properties.copyNgram()),
			materialGrams);
		Set<String> noteGrams = new HashSet<>();
		for (String note : citedNotes) {
			noteGrams.addAll(CharNgrams.of(note, properties.copyNgram()));
		}
		double notesRatio = CharNgrams.containment(noteGrams, materialGrams);
		return new Ratios(answerRatio, notesRatio, Math.max(answerRatio, notesRatio));
	}

	/** 소수 둘째 자리(DB 컬럼 DECIMAL(3,2))로 반올림한 계수 */
	public BigDecimal factor(double ratio) {
		BigDecimal min = properties.copyMinFactor();
		if (ratio <= properties.copyLowRatio()) {
			return BigDecimal.ONE.setScale(2);
		}
		if (ratio >= properties.copyHighRatio()) {
			return min.setScale(2, RoundingMode.HALF_UP);
		}
		double progress = (ratio - properties.copyLowRatio()) / (properties.copyHighRatio() - properties.copyLowRatio());
		BigDecimal drop = BigDecimal.ONE.subtract(min).multiply(BigDecimal.valueOf(progress));
		return BigDecimal.ONE.subtract(drop).setScale(2, RoundingMode.HALF_UP);
	}

}
