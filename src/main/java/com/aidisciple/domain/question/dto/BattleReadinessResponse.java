package com.aidisciple.domain.question.dto;

/** Q-01. 나에게 아직 안 나온 문항의 수만 알려 준다 (문항 내용은 담지 않는다). */
public record BattleReadinessResponse(boolean ready, int availableForMe, int required) {
}
