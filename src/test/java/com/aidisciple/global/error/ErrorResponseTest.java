package com.aidisciple.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 오류 응답의 JSON 모양. FE 가 타입을 만드는 계약이라 필드가 새거나 사라지지 않게 고정한다. */
class ErrorResponseTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void details가_없으면_필드를_내보내지_않아_기존_응답_모양과_같다() {
		JsonNode json = mapper.valueToTree(ErrorResponse.of(ErrorCode.NOT_FOUND));

		assertThat(json.has("details")).isFalse();
		assertThat(json.get("code").asString()).isEqualTo("NOT_FOUND");
		assertThat(json.get("violations").isArray()).isTrue();
	}

	@Test
	void details가_있으면_함께_내보낸다() {
		JsonNode json = mapper.valueToTree(
			ErrorResponse.of(ErrorCode.COURSE_DUPLICATE, "중복", Map.of("existingCourseId", 12)));

		assertThat(json.get("code").asString()).isEqualTo("COURSE_DUPLICATE");
		assertThat(json.get("details").get("existingCourseId").asInt()).isEqualTo(12);
	}

	@Test
	void BusinessException의_details가_응답까지_이어진다() {
		BusinessException exception = new BusinessException(ErrorCode.UNIT_DUPLICATE, Map.of("existingUnitId", 5L));

		assertThat(exception.getDetails()).containsEntry("existingUnitId", 5L);
		assertThat(new BusinessException(ErrorCode.NOT_FOUND).getDetails()).isEmpty();
		assertThat(new BusinessException(ErrorCode.NOT_FOUND, "메시지").getDetails()).isEmpty();
	}

}
