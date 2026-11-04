package com.aidisciple.global.text;

import java.util.HashSet;
import java.util.Set;

/**
 * 글자 n-gram 비교. 모두 compact(정규화 + 공백 제거) 문자열 기준이다.
 * - containment: source 의 n-gram 중 target 에도 있는 비율 (방향 있음)
 *     노트 검증 — content 의 3-gram 이 학생 메시지에 포함되는 비율 ≥ 0.6 (api-spec 11-1)
 *     복붙 보정 — 답·노트의 8-gram 이 자료에 포함되는 비율 (api-spec 11-4)
 * - similarity: 두 문자열이 얼마나 닮았나 (Dice 계수, 대칭)
 *     노트 병합 ≥0.9 자동 / 0.75~0.9 제안, 문항 중복 ≥0.8
 */
public final class CharNgrams {

	private CharNgrams() {
	}

	/** n-gram 집합. compact 길이가 n 보다 짧으면 빈 집합 (짧은 문자열은 아래 메서드가 따로 처리한다) */
	public static Set<String> of(String text, int n) {
		if (n < 1) {
			throw new IllegalArgumentException("n 은 1 이상이어야 합니다: " + n);
		}
		String s = TextNormalizer.compact(text);
		Set<String> grams = new HashSet<>();
		for (int i = 0; i + n <= s.length(); i++) {
			grams.add(s.substring(i, i + n));
		}
		return grams;
	}

	/** source 의 n-gram 중 target 에도 있는 비율 (0~1). source 가 n 보다 짧으면 target 에 통째로 들어 있는지로 본다. */
	public static double containment(String source, String target, int n) {
		String s = TextNormalizer.compact(source);
		if (s.isEmpty()) {
			return 0.0;
		}
		if (s.length() < n) {
			return TextNormalizer.compact(target).contains(s) ? 1.0 : 0.0;
		}
		return containment(of(source, n), of(target, n));
	}

	/** 미리 만든 집합끼리 비교 (자료 문장처럼 여러 번 쓰는 쪽을 한 번만 만들 때) */
	public static double containment(Set<String> source, Set<String> target) {
		if (source.isEmpty()) {
			return 0.0;
		}
		long hit = source.stream().filter(target::contains).count();
		return (double) hit / source.size();
	}

	/** 두 문자열의 유사도 (Dice 계수, 0~1, 대칭). 한쪽이라도 n 보다 짧으면 같은지로만 본다. */
	public static double similarity(String a, String b, int n) {
		String x = TextNormalizer.compact(a);
		String y = TextNormalizer.compact(b);
		if (x.isEmpty() || y.isEmpty()) {
			return 0.0;
		}
		if (x.length() < n || y.length() < n) {
			return x.equals(y) ? 1.0 : 0.0;
		}
		Set<String> gx = of(a, n);
		Set<String> gy = of(b, n);
		long common = gx.stream().filter(gy::contains).count();
		return 2.0 * common / (gx.size() + gy.size());
	}
}
