package com.aidisciple.domain.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** X-01 문항 생성 수동 실행. targetCount 를 생략하면 설정값(app.question.target-count) */
public record CreateQuestionJobRequest(@Min(1) @Max(30) Integer targetCount) {
}
