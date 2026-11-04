package com.aidisciple.ai.gemini;

/**
 * Gemini 와 실제로 통신하는 가장 얇은 통로. SDK 코드는 이 인터페이스의 구현체에만 둔다.
 * 재시도·한도·파싱·기록은 GeminiLlmClient 가 맡으므로, 구현체는 한 번 부르고 결과나 분류된 예외만 돌려준다.
 */
public interface GeminiApi {

	/** API 키가 설정돼 있는가 */
	boolean isConfigured();

	/**
	 * 프롬프트를 보내 JSON 응답 텍스트를 받는다.
	 * @param responseType 응답 스키마를 만들 때 쓸 타입 (구현체가 JSON schema 로 바꿔 요청에 붙인다)
	 * @throws GeminiApiException 실패를 종류별로 분류해서 던진다
	 */
	GeminiRawResponse generate(String prompt, Class<?> responseType);
}
