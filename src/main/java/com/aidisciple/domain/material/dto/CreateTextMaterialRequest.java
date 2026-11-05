package com.aidisciple.domain.material.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * M-02 텍스트 붙여넣기. 글자 수 상한과 동의 여부는 서비스가 검사한다.
 * FE 가 분기하는 MATERIAL_TOO_LARGE·MATERIAL_CONSENT_REQUIRED 를 주기 위해서다 (Bean Validation 이면 INVALID_INPUT 이 나간다).
 */
public record CreateTextMaterialRequest(
	@NotBlank @Size(max = 200) String title,
	@NotBlank String text,
	boolean consentAgreed
) {
}
