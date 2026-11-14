package com.aidisciple.domain.question.logic;

/** 코드 채점 결과. AMBIGUOUS 는 코드로 가를 수 없는 답이다 (채점에서는 LLM 판정으로, 누출 시험에서는 맞히지 않은 것으로 본다). */
public enum CodeVerdict {
	CORRECT,
	WRONG,
	AMBIGUOUS
}
