package com.aidisciple.domain.question.logic;

/** 프롬프트에 넣은 주차 문장. {@code no} 는 프롬프트용 번호(1부터), {@code sentenceId} 는 material_sentences.id */
public record SourceSentence(int no, long sentenceId, String text) {
}
