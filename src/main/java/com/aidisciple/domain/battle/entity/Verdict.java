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
	UNJUDGED
}
