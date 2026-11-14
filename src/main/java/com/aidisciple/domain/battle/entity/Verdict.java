package com.aidisciple.domain.battle.entity;

/**
 * DB {@code battle_answers.verdict}. PARTIAL 은 LLM 판정에서만 나오고,
 * ZEROED_INJECTION 은 지시문 의심으로 0점, UNJUDGED 는 판정 실패다.
 */
public enum Verdict {
	CORRECT,
	PARTIAL,
	WRONG,
	UNKNOWN,
	ZEROED_INJECTION,
	UNJUDGED,
	/** 풀이는 끝났지만 아직 채점하지 않은 답(WP8). 채점(WP9)이 다른 값으로 바꾸므로 끝난 배틀에는 남지 않는다. */
	PENDING
}
