package com.aidisciple.domain.battle.dto;

import jakarta.validation.constraints.NotNull;

/** B-08 내 노트 공개 동의 변경. 두 사람이 모두 동의해야 상대의 답과 노트를 볼 수 있다. */
public record UpdateParticipantRequest(@NotNull Boolean notesPublic) {
}
