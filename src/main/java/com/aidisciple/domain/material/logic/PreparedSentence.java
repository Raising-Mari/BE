package com.aidisciple.domain.material.logic;

/**
 * 저장할 문장 한 줄. {@code seq} 는 자료 안의 순서(1부터), {@code normalizedText} 는 비교용 정규화본,
 * {@code textHash} 는 그 정규화본의 SHA-256 이다.
 */
public record PreparedSentence(int seq, Integer pageNo, String text, String normalizedText, String textHash) {
}
