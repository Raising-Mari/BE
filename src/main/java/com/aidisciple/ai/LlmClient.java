package com.aidisciple.ai;

public interface LlmClient {
	<T> T generateJson(LlmRequest request, Class<T> responseType);
}
