package com.aidisciple.ai.gemini;

import com.aidisciple.ai.AiException;
import com.aidisciple.global.error.ErrorCode;

/** 재시도 대기. 테스트에서는 실제로 기다리지 않는 구현을 넣는다. */
@FunctionalInterface
public interface Sleeper {

	void sleep(long millis);

	Sleeper REAL = millis -> {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiException(ErrorCode.AI_UNAVAILABLE, e);
		}
	};
}
