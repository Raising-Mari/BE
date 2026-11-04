package com.aidisciple.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 테스트용 가짜 LlmClient. 실제 AI를 부르지 않고, 미리 넣어 둔 응답을 순서대로 돌려준다.
 */
public class FakeLlmClient implements LlmClient {

	private final Deque<Object> responses = new ArrayDeque<>();
	private final List<LlmRequest> requests = new ArrayList<>();

	/** 다음 호출에 돌려줄 응답을 넣는다. */
	public FakeLlmClient willReturn(Object response) {
		responses.addLast(response);
		return this;
	}

	/** 다음 호출에서 던질 예외를 넣는다. (AI 장애 흉내) */
	public FakeLlmClient willThrow(RuntimeException exception) {
		responses.addLast(exception);
		return this;
	}

	@Override
	public <T> T generateJson(LlmRequest request, Class<T> responseType) {
		requests.add(request);
		Object next = responses.pollFirst();
		if (next == null) {
			throw new IllegalStateException(
				"FakeLlmClient: 준비된 응답이 없습니다. purpose=" + request.purpose());
		}
		if (next instanceof RuntimeException exception) {
			throw exception;
		}
		return responseType.cast(next);
	}

	/** 지금까지 받은 요청 (어떤 프롬프트·변수로 불렀는지 검증용) */
	public List<LlmRequest> requests() {
		return List.copyOf(requests);
	}

	public int callCount() {
		return requests.size();
	}
}
