package com.aidisciple.global.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** 목록 API 의 공통 응답. 필드는 api-spec 0절의 페이지네이션 규약과 같다. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, boolean hasNext) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
	}

	/** 엔티티 페이지를 응답 DTO 로 바꿔 담는다. 엔티티를 API 로 직접 내보내지 않기 위한 경로. */
	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
			page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
	}

}
