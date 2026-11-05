package com.aidisciple.global.api;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * API 계약 스켈레톤(WP 2-C)에서 아직 구현하지 않은 엔드포인트가 501 을 돌려주게 한다.
 * 컨트롤러의 반환 타입을 응답 DTO 로 두어야 Swagger 에 응답 모양이 나오므로 제네릭으로 받는다.
 * 기능을 구현하면 이 호출을 서비스 호출로 바꾸고, 남은 호출은 {@code NotImplemented.yet} 으로 찾는다.
 */
public final class NotImplemented {

	private NotImplemented() {
	}

	public static <T> T yet() {
		throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
	}

}
