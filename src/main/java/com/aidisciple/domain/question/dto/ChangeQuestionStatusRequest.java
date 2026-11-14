package com.aidisciple.domain.question.dto;

import jakarta.validation.constraints.NotNull;

import com.aidisciple.domain.question.entity.QuestionStatus;

/** X-05 문항 상태 변경 (UNJUDGED 로는 바꿀 수 없다) */
public record ChangeQuestionStatusRequest(@NotNull QuestionStatus status) {
}
