package com.aidisciple.domain.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.aidisciple.domain.auth.dto.UserRef;
import com.aidisciple.domain.auth.service.UserLookupService;
import com.aidisciple.domain.course.service.CourseService;
import com.aidisciple.domain.material.dto.MaterialResponse;
import com.aidisciple.domain.material.dto.MaterialSummaryResponse;
import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialFailReason;
import com.aidisciple.domain.material.entity.MaterialSourceType;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;
import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

class MaterialQueryServiceTest {

	private static final long USER_ID = 42L;
	private static final long OTHER_USER_ID = 43L;
	private static final long COURSE_ID = 5L;

	private final MaterialRepository materialRepository = mock(MaterialRepository.class);
	private final MaterialUnitRepository materialUnitRepository = mock(MaterialUnitRepository.class);
	private final UserLookupService userLookupService = mock(UserLookupService.class);
	private final CourseService courseService = mock(CourseService.class);
	private final MaterialQueryService service = new MaterialQueryService(materialRepository, materialUnitRepository,
		userLookupService, courseService);

	private final UserRef user = new UserRef(USER_ID, 1L, null);

	@BeforeEach
	void 기본_상태() {
		when(userLookupService.requireActive(USER_ID)).thenReturn(user);
		when(userLookupService.requireActive(OTHER_USER_ID)).thenReturn(new UserRef(OTHER_USER_ID, 1L, null));
	}

	private static Material material(long id, long uploaderId, MaterialStatus status) {
		Material material = Material.accept(COURSE_ID, uploaderId, "자료 " + id, MaterialSourceType.TEXT, null, null,
			Instant.parse("2026-11-06T00:00:00Z"));
		ReflectionTestUtils.setField(material, "id", id);
		ReflectionTestUtils.setField(material, "status", status);
		return material;
	}

	private static BusinessException thrownBy(Runnable action) {
		Throwable thrown = catchThrowable(action::run);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	// --- M-03 ---

	@Test
	void 올린_사람은_상태와_연결된_주차를_본다() {
		Material done = material(1L, USER_ID, MaterialStatus.DONE);
		ReflectionTestUtils.setField(done, "sentenceCount", 120);
		when(materialRepository.findById(1L)).thenReturn(Optional.of(done));
		when(materialUnitRepository.findByIdMaterialId(1L)).thenReturn(List.of(MaterialUnit.link(1L, 9L), MaterialUnit.link(1L, 3L)));

		MaterialResponse response = service.get(USER_ID, 1L);

		assertThat(response).isEqualTo(new MaterialResponse(1L, "자료 1", MaterialStatus.DONE, null, 120, List.of(3L, 9L)));
	}

	@Test
	void 실패한_자료는_실패_이유를_함께_준다() {
		Material failed = material(2L, USER_ID, MaterialStatus.FAILED);
		ReflectionTestUtils.setField(failed, "failReason", MaterialFailReason.NO_TEXT);
		when(materialRepository.findById(2L)).thenReturn(Optional.of(failed));
		when(materialUnitRepository.findByIdMaterialId(2L)).thenReturn(List.of());

		MaterialResponse response = service.get(USER_ID, 2L);

		assertThat(response.status()).isEqualTo(MaterialStatus.FAILED);
		assertThat(response.failReason()).isEqualTo(MaterialFailReason.NO_TEXT);
		assertThat(response.unitIds()).isEmpty();
	}

	@Test
	void 남의_자료와_삭제된_자료와_없는_자료는_똑같이_NOT_FOUND다() {
		when(materialRepository.findById(3L)).thenReturn(Optional.of(material(3L, OTHER_USER_ID, MaterialStatus.DONE)));
		when(materialRepository.findById(4L)).thenReturn(Optional.of(material(4L, USER_ID, MaterialStatus.DELETED)));
		when(materialRepository.findById(5L)).thenReturn(Optional.empty());

		assertThat(thrownBy(() -> service.get(USER_ID, 3L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(thrownBy(() -> service.get(USER_ID, 4L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(thrownBy(() -> service.get(USER_ID, 5L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

	// --- M-04 ---

	@Test
	void 내가_올린_자료는_주차_연결과_함께_최근_것부터_준다() {
		when(materialRepository.findByCourseIdAndUploaderIdAndStatusNotOrderByIdDesc(COURSE_ID, USER_ID, MaterialStatus.DELETED))
			.thenReturn(List.of(material(11L, USER_ID, MaterialStatus.DONE), material(10L, USER_ID, MaterialStatus.PENDING)));
		when(materialUnitRepository.findByIdMaterialIdIn(anyCollection())).thenReturn(List.of(
			MaterialUnit.link(10L, 7L), MaterialUnit.link(10L, 2L), MaterialUnit.link(11L, 5L)));

		List<MaterialSummaryResponse> result = service.mine(USER_ID, COURSE_ID);

		assertThat(result).extracting(MaterialSummaryResponse::id).containsExactly(11L, 10L);
		assertThat(result.get(0).unitIds()).containsExactly(5L);
		assertThat(result.get(1).unitIds()).containsExactly(2L, 7L);
	}

	@Test
	void 주차가_연결되지_않은_자료는_빈_목록이다() {
		when(materialRepository.findByCourseIdAndUploaderIdAndStatusNotOrderByIdDesc(COURSE_ID, USER_ID, MaterialStatus.DELETED))
			.thenReturn(List.of(material(11L, USER_ID, MaterialStatus.DONE)));
		when(materialUnitRepository.findByIdMaterialIdIn(anyCollection())).thenReturn(List.of());

		assertThat(service.mine(USER_ID, COURSE_ID).get(0).unitIds()).isEmpty();
	}

	@Test
	void 볼_수_없는_과목의_자료_목록은_조회하지_않는다() {
		when(courseService.requireVisible(6L, user)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

		assertThat(thrownBy(() -> service.mine(USER_ID, 6L)).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

}
