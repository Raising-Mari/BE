package com.aidisciple.domain.question.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.aidisciple.domain.question.entity.GradingMode;

/**
 * X-06 문항 직접 추가. ANSWER_LIST 면 {@code answers}(정답 표현), KEY_ELEMENTS 면 {@code keyElements}·{@code requiredElements}
 * (생략하면 요소 전부) 가 필요하다. 근거 문장은 강의자료 문장과 연결하지 않고 글로만 남긴다.
 */
public record CreateManualQuestionRequest(
	@NotNull Long unitId,
	@NotBlank @Size(max = 2000) String stem,
	@NotNull GradingMode gradingMode,
	@Size(max = 20) List<@NotBlank @Size(max = 200) String> answers,
	@Size(max = 10) List<@Valid KeyElementInput> keyElements,
	Integer requiredElements,
	@NotBlank @Size(max = 4000) String referenceAnswer,
	@NotBlank @Size(max = 4000) String evidenceText
) {

	public record KeyElementInput(@NotBlank @Size(max = 100) String label, @Size(max = 10) List<@NotBlank String> aliases) {
	}

}
