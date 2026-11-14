package com.aidisciple.domain.battle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** B-02. 코드는 6자리지만 대소문자·공백을 서버가 정리하므로 입력 그대로 받는다. */
public record JoinBattleRequest(@NotBlank @Size(max = 20) String roomCode) {
}
