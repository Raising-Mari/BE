package com.aidisciple.domain.teaching.dto;

import jakarta.validation.constraints.NotNull;

/** T-05 확인 카드에서 "합치기". mergeEntryId 가 keepEntryId 에 흡수된다(보통 keep 은 새 노트). */
public record MergeNotesRequest(@NotNull Long keepEntryId, @NotNull Long mergeEntryId) {
}
