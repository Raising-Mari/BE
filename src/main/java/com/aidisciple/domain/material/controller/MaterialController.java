package com.aidisciple.domain.material.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aidisciple.domain.material.dto.CreateTextMaterialRequest;
import com.aidisciple.domain.material.dto.MaterialAcceptedResponse;
import com.aidisciple.domain.material.dto.MaterialResponse;
import com.aidisciple.domain.material.dto.MaterialSummaryResponse;
import com.aidisciple.domain.material.dto.MaterialUnitsResponse;
import com.aidisciple.domain.material.dto.UpdateMaterialUnitsRequest;
import com.aidisciple.domain.material.service.MaterialIntakeService;
import com.aidisciple.domain.material.service.MaterialQueryService;
import com.aidisciple.domain.material.service.MaterialUnitService;
import com.aidisciple.global.security.AuthUser;
import com.aidisciple.global.security.CurrentUser;

/** 3절 강의자료 (WP5). 자료는 과목에 올리고 주차는 M-06 으로 나중에 고른다. */
@Tag(name = "강의자료")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MaterialController {

	private final MaterialIntakeService intakeService;
	private final MaterialQueryService queryService;
	private final MaterialUnitService unitService;

	/** 동의 여부는 서비스가 검사해 MATERIAL_CONSENT_REQUIRED 를 준다. 그래서 값이 없을 때도 400 이 아니라 false 로 받는다. */
	@Operation(summary = "M-01 PDF 업로드 (multipart, 202 접수)")
	@PostMapping(value = "/courses/{courseId}/materials/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MaterialAcceptedResponse uploadFile(
		@CurrentUser AuthUser user,
		@PathVariable Long courseId,
		@RequestPart("file") MultipartFile file,
		@RequestParam("title") String title,
		@RequestParam(value = "consentAgreed", defaultValue = "false") boolean consentAgreed
	) {
		return intakeService.acceptFile(user.userId(), courseId, file, title, consentAgreed);
	}

	@Operation(summary = "M-02 텍스트 붙여넣기 (202 접수)")
	@PostMapping("/courses/{courseId}/materials/text")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MaterialAcceptedResponse uploadText(
		@CurrentUser AuthUser user,
		@PathVariable Long courseId,
		@Valid @RequestBody CreateTextMaterialRequest request
	) {
		return intakeService.acceptText(user.userId(), courseId, request);
	}

	@Operation(summary = "M-03 처리 상태 + 연결된 주차 (폴링)")
	@GetMapping("/materials/{materialId}")
	public MaterialResponse getMaterial(@CurrentUser AuthUser user, @PathVariable Long materialId) {
		return queryService.get(user.userId(), materialId);
	}

	@Operation(summary = "M-04 내가 올린 자료 목록")
	@GetMapping("/courses/{courseId}/materials")
	public List<MaterialSummaryResponse> myMaterials(@CurrentUser AuthUser user, @PathVariable Long courseId) {
		return queryService.mine(user.userId(), courseId);
	}

	@Operation(summary = "M-06 자료가 출처가 될 주차 지정 (전체 교체)")
	@PutMapping("/materials/{materialId}/units")
	public MaterialUnitsResponse updateUnits(
		@CurrentUser AuthUser user,
		@PathVariable Long materialId,
		@Valid @RequestBody UpdateMaterialUnitsRequest request
	) {
		return unitService.replace(user.userId(), materialId, request);
	}

}
