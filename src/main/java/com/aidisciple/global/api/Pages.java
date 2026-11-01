package com.aidisciple.global.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/** 쿼리 문자열의 {@code page}·{@code size} 를 규약에 맞는 {@link Pageable} 로 바꾼다. */
public final class Pages {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;

	private Pages() {
	}

	/** 음수 page 는 거부하고, 너무 큰 size 는 오류 대신 최대값으로 줄인다. 정렬은 쿼리에서 정한다. */
	public static Pageable of(Integer page, Integer size) {
		return of(page, size, Sort.unsorted());
	}

	public static Pageable of(Integer page, Integer size, Sort sort) {
		int pageNo = page == null ? 0 : page;
		if (pageNo < 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상이어야 합니다.");
		}
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageSize < 1) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "size 는 1 이상이어야 합니다.");
		}
		return PageRequest.of(pageNo, Math.min(pageSize, MAX_SIZE), sort);
	}

}
