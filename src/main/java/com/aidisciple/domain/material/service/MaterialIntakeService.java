package com.aidisciple.domain.material.service;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.material.config.MaterialProperties;
import com.aidisciple.domain.material.dto.CreateTextMaterialRequest;
import com.aidisciple.domain.material.dto.MaterialAcceptedResponse;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.logic.UploadValidator;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

/**
 * M-01·M-02 접수. <b>클래스에 트랜잭션을 걸지 않는다</b>: 자료 행을 먼저 커밋해야, 곧바로 시작되는 처리 스레드가 그 행을 볼 수 있다.
 * 검사 → 접수(DB 에 PENDING 으로 저장) → 처리 작업을 큐에 넣고 202 로 답한다. 처리 결과는 M-03 으로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class MaterialIntakeService {

	static final String RATE_LIMIT_POLICY = "material.upload";
	private static final int MAX_TITLE_LENGTH = 200;

	private final UserLookupService userLookupService;
	private final CourseService courseService;
	private final MaterialStateService stateService;
	private final MaterialProcessor processor;
	private final RateLimiter rateLimiter;
	private final RateLimitProperties rateLimitProperties;
	private final MaterialProperties properties;

	public MaterialAcceptedResponse acceptFile(long userId, long courseId, MultipartFile file, String title, boolean consentAgreed) {
		UserRef user = userLookupService.requireActive(userId);
		courseService.requireVisible(courseId, user);
		requireConsent(consentAgreed);
		String cleanTitle = cleanTitle(title);
		rateLimiter.check(RATE_LIMIT_POLICY, rateLimitProperties.materialUpload(), String.valueOf(userId));

		byte[] bytes = read(file);
		UploadValidator.requirePdf(file.getOriginalFilename(), file.getContentType(), bytes);

		long materialId = stateService.register(courseId, userId, cleanTitle, MaterialSourceType.PDF,
			UploadValidator.cleanFilename(file.getOriginalFilename()), (long) bytes.length);
		return dispatch(materialId, new MaterialContent.Pdf(bytes));
	}

	public MaterialAcceptedResponse acceptText(long userId, long courseId, CreateTextMaterialRequest request) {
		UserRef user = userLookupService.requireActive(userId);
		courseService.requireVisible(courseId, user);
		requireConsent(request.consentAgreed());
		String cleanTitle = cleanTitle(request.title());
		if (request.text().length() > properties.maxTextChars()) {
			throw new BusinessException(ErrorCode.MATERIAL_TOO_LARGE);
		}
		rateLimiter.check(RATE_LIMIT_POLICY, rateLimitProperties.materialUpload(), String.valueOf(userId));

		long materialId = stateService.register(courseId, userId, cleanTitle, MaterialSourceType.TEXT, null, null);
		return dispatch(materialId, new MaterialContent.Text(request.text()));
	}

	/** 동의는 FE 에서도 막지만 서버가 다시 검사한다. 값이 없으면 동의하지 않은 것이다. */
	private static void requireConsent(boolean consentAgreed) {
		if (!consentAgreed) {
			throw new BusinessException(ErrorCode.MATERIAL_CONSENT_REQUIRED);
		}
	}

	private static String cleanTitle(String title) {
		String stripped = title == null ? "" : title.strip();
		if (stripped.isEmpty() || stripped.length() > MAX_TITLE_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "자료 제목은 1~200자로 입력해 주세요.");
		}
		return stripped;
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.MATERIAL_INVALID_FILE);
		}
	}

	private MaterialAcceptedResponse dispatch(long materialId, MaterialContent content) {
		try {
			processor.processAsync(materialId, content);
		} catch (TaskRejectedException e) {
			// 처리 큐가 가득 찼다. PENDING 으로 남겨 두면 영영 처리되지 않으므로 접수를 취소하고 다시 시도하게 한다.
			stateService.abandon(materialId);
			throw new BusinessException(ErrorCode.RATE_LIMITED, "지금 처리 중인 자료가 많습니다. 잠시 후 다시 올려 주세요.");
		}
		return new MaterialAcceptedResponse(materialId, MaterialStatus.PENDING);
	}

}
