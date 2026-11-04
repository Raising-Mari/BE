package com.aidisciple.ai;

import com.aidisciple.global.error.BusinessException;
import com.aidisciple.global.error.ErrorCode;

/**
 * AI 호출 실패 전용 예외. 호출한 쪽(가르치기·배틀)이 AI 실패만 골라 잡아 사용자 안내를 정할 수 있게 한다.
 * 메시지는 ErrorCode 의 사용자용 문구만 쓰고, 내부 원인은 cause 로만 남긴다.
 */
public class AiException extends BusinessException {

	public AiException(ErrorCode errorCode) {
		super(errorCode);
	}

	public AiException(ErrorCode errorCode, Throwable cause) {
		super(errorCode);
		initCause(cause);
	}
}
