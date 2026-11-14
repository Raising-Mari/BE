package com.aidisciple.ai;

import java.util.Map;

public record LlmRequest(
	AiPurpose purpose,               // ai_call_logs.purpose
	String promptName,               // 예: "teaching-turn"
	String promptVersion,            // 예: "v1"
	Map<String, String> variables,   // {{변수}} 치환값
	Long userId,                     // 로그용 (FK 아님)
	String relatedType,              // 예: "BATTLE"
	Long relatedId
) {}
