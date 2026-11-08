package com.aidisciple.domain.battle.dto;

import java.util.List;

import com.aidisciple.domain.battle.entity.BattleMode;
import com.aidisciple.domain.battle.entity.BattleStatus;

/** B-10. {@code skippedAnswerIds} 는 문항이 신고·폐기돼 다시 내지 못한 답이다. */
public record CreatePracticeResponse(Long id, BattleMode mode, BattleStatus status, int questionCount,
									 List<Long> skippedAnswerIds) {
}
