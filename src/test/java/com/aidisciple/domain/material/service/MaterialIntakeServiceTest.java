package com.aidisciple.domain.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.material.config.MaterialProperties;
import com.aidisciple.domain.material.dto.CreateTextMaterialRequest;
import com.aidisciple.domain.material.dto.MaterialAcceptedResponse;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;
import com.aidisciple.global.ratelimit.RateLimitProperties;
import com.aidisciple.global.ratelimit.RateLimiter;

class MaterialIntakeServiceTest {

	private static final long USER_ID = 42L;
	private static final long COURSE_ID = 5L;
	private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

	private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final CourseService courseService = mock(CourseService.class);
	private final MaterialStateService stateService = mock(MaterialStateService.class);
	private final MaterialProcessor processor = mock(MaterialProcessor.class);
	private final RateLimitProperties limits = new RateLimitProperties(null, null, null,
		new RateLimitProperties.Rule(2, Duration.ofHours(1)));
	private final MaterialIntakeService service = new MaterialIntakeService(userLookupService, courseService, stateService,
		processor, new RateLimiter(clock), limits, MaterialProperties.defaults());

	private final UserRef user = new UserRef(USER_ID, 1L, null);

	@BeforeEach
	void 기본_상태() {
		when(userLookupService.requireActive(USER_ID)).thenReturn(user);
		when(stateService.register(anyLong(), anyLong(), anyString(), any(), any(), any())).thenReturn(77L);
	}

	private static MockMultipartFile pdfFile() {
		return new MockMultipartFile("file", "lecture.pdf", "application/pdf", PDF);
	}

	private static BusinessException thrownBy(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	// --- M-01 ---

	@Test
	void PDF를_접수하면_PENDING으로_저장하고_처리_작업을_큐에_넣는다() {
		MaterialAcceptedResponse response = service.acceptFile(USER_ID, COURSE_ID, pdfFile(), "  자료구조 3주차  ", true);

		assertThat(response).isEqualTo(new MaterialAcceptedResponse(77L, MaterialStatus.PENDING));
		verify(stateService).register(COURSE_ID, USER_ID, "자료구조 3주차", MaterialSourceType.PDF, "lecture.pdf", (long) PDF.length);
		ArgumentCaptor<MaterialContent> content = ArgumentCaptor.forClass(MaterialContent.class);
		verify(processor).processAsync(eq(77L), content.capture());
		assertThat(content.getValue()).isInstanceOfSatisfying(MaterialContent.Pdf.class,
			pdf -> assertThat(pdf.bytes()).isEqualTo(PDF));
	}

	@Test
	void 동의하지_않으면_파일을_읽기_전에_거부한다() {
		assertThat(thrownBy(() -> service.acceptFile(USER_ID, COURSE_ID, pdfFile(), "제목", false)).getErrorCode())
			.isEqualTo(ErrorCode.MATERIAL_CONSENT_REQUIRED);

		verify(stateService, never()).register(anyLong(), anyLong(), anyString(), any(), any(), any());
		verifyNoInteractions(processor);
	}

	@Test
	void PDF가_아니면_접수하지_않는다() {
		MockMultipartFile text = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
		MockMultipartFile renamed = new MockMultipartFile("file", "fake.pdf", "application/pdf", "MZ not a pdf".getBytes(StandardCharsets.UTF_8));

		assertThat(thrownBy(() -> service.acceptFile(USER_ID, COURSE_ID, text, "제목", true)).getErrorCode())
			.isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		assertThat(thrownBy(() -> service.acceptFile(USER_ID, COURSE_ID, renamed, "제목", true)).getErrorCode())
			.isEqualTo(ErrorCode.MATERIAL_INVALID_FILE);
		verify(stateService, never()).register(anyLong(), anyLong(), anyString(), any(), any(), any());
	}

	@Test
	void 제목이_비었거나_200자를_넘으면_거부한다() {
		for (String title : new String[] {null, "", "   ", "가".repeat(201)}) {
			assertThat(thrownBy(() -> service.acceptFile(USER_ID, COURSE_ID, pdfFile(), title, true)).getErrorCode())
				.as("제목: %s", title == null ? null : title.length() + "자").isEqualTo(ErrorCode.INVALID_INPUT);
		}
		verify(stateService, never()).register(anyLong(), anyLong(), anyString(), any(), any(), any());
	}

	@Test
	void 볼_수_없는_과목에는_올릴_수_없다() {
		when(courseService.requireVisible(6L, user)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(thrownBy(() -> service.acceptFile(USER_ID, 6L, pdfFile(), "제목", true)).getErrorCode())
			.isEqualTo(ErrorCode.NOT_FOUND);
		verify(stateService, never()).register(anyLong(), anyLong(), anyString(), any(), any(), any());
	}

	@Test
	void 한도를_넘으면_파일을_읽기_전에_RATE_LIMITED다() {
		service.acceptFile(USER_ID, COURSE_ID, pdfFile(), "하나", true);
		service.acceptText(USER_ID, COURSE_ID, new CreateTextMaterialRequest("둘", "붙여넣은 글", true));

		assertThat(thrownBy(() -> service.acceptFile(USER_ID, COURSE_ID, pdfFile(), "셋", true)).getErrorCode())
			.isEqualTo(ErrorCode.RATE_LIMITED);
	}

	@Test
	void 처리_큐가_가득_차면_접수를_취소하고_다시_올리게_한다() {
		doThrow(new TaskRejectedException("queue full")).when(processor).processAsync(anyLong(), any());

		BusinessException e = thrownBy(() -> service.acceptFile(USER_ID, COURSE_ID, pdfFile(), "제목", true));

		assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
		verify(stateService).abandon(77L);
	}

	// --- M-02 ---

	@Test
	void 붙여넣은_글을_접수한다() {
		MaterialAcceptedResponse response = service.acceptText(USER_ID, COURSE_ID,
			new CreateTextMaterialRequest("  제목  ", "스택은 후입선출 구조이다.", true));

		assertThat(response).isEqualTo(new MaterialAcceptedResponse(77L, MaterialStatus.PENDING));
		verify(stateService).register(COURSE_ID, USER_ID, "제목", MaterialSourceType.TEXT, null, null);
		ArgumentCaptor<MaterialContent> content = ArgumentCaptor.forClass(MaterialContent.class);
		verify(processor).processAsync(eq(77L), content.capture());
		assertThat(content.getValue()).isEqualTo(new MaterialContent.Text("스택은 후입선출 구조이다."));
	}

	@Test
	void 글자_수_상한_안에서는_통과하고_넘으면_TOO_LARGE다() {
		MaterialIntakeService small = new MaterialIntakeService(userLookupService, courseService, stateService, processor,
			new RateLimiter(clock), limits, new MaterialProperties(10, 500_000, 500, 30));

		small.acceptText(USER_ID, COURSE_ID, new CreateTextMaterialRequest("제목", "가".repeat(10), true));
		assertThat(thrownBy(() -> small.acceptText(USER_ID, COURSE_ID,
			new CreateTextMaterialRequest("제목", "가".repeat(11), true))).getErrorCode())
			.isEqualTo(ErrorCode.MATERIAL_TOO_LARGE);
	}

	@Test
	void 글을_붙여넣을_때도_동의가_필요하다() {
		assertThat(thrownBy(() -> service.acceptText(USER_ID, COURSE_ID, new CreateTextMaterialRequest("제목", "글", false))).getErrorCode())
			.isEqualTo(ErrorCode.MATERIAL_CONSENT_REQUIRED);
		verifyNoInteractions(processor);
	}

}
