package com.aidisciple.domain.question.entity;

/** DB {@code question_generation_jobs.trigger_type} — 무엇 때문에 문항 생성 작업이 걸렸나 */
public enum JobTriggerType {
	MATERIAL_DONE,
	UNIT_MAPPED,
	LOW_STOCK,
	ADMIN
}
