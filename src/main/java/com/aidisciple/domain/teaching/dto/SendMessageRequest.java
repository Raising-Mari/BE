package com.aidisciple.domain.teaching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * T-01. {@code clientMessageId} 는 FE 가 만든 UUID 다. 같은 값으로 다시 보내면 저장된 결과를 그대로 돌려준다(중복 전송 방지).
 */
public record SendMessageRequest(
	@NotNull Long unitId,
	@NotBlank @Size(max = 2000) String content,
	@NotBlank @Size(min = 36, max = 36) String clientMessageId
) {
}
