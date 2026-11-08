package com.aidisciple.domain.question.entity;

/** DB {@code question_validation_logs.stage}. 문항 하나가 어느 단계에서 통과·탈락했는지 남긴다. */
public enum ValidationStage {
	GENERATE,
	EVIDENCE_CHECK,
	LEAK_TEST,
	EXTERNAL_JUDGE
}
