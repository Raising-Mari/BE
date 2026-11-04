package com.aidisciple.ai.log;

import com.aidisciple.ai.AiPurpose;
import com.aidisciple.global.jpa.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ai_call_logs. 호출 1회 = 1행. 프롬프트·응답 원문과 학생 입력은 저장하지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_call_logs")
public class AiCallLog extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AiPurpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AiProvider provider;

	@Column(nullable = false, length = 50)
	private String model;

	@Column(name = "prompt_name", nullable = false, length = 50)
	private String promptName;

	@Column(name = "prompt_version", nullable = false, length = 10)
	private String promptVersion;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "latency_ms", nullable = false)
	private int latencyMs;

	@Column(nullable = false)
	private boolean success;

	@Column(name = "error_code", length = 40)
	private String errorCode;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "related_type", length = 30)
	private String relatedType;

	@Column(name = "related_id")
	private Long relatedId;

	public static AiCallLog from(AiCallRecord r) {
		AiCallLog log = new AiCallLog();
		log.purpose = r.purpose();
		log.provider = r.provider();
		log.model = r.model();
		log.promptName = r.promptName();
		log.promptVersion = r.promptVersion();
		log.inputTokens = r.inputTokens();
		log.outputTokens = r.outputTokens();
		log.latencyMs = r.latencyMs();
		log.success = r.success();
		log.errorCode = r.errorCode();
		log.userId = r.userId();
		log.relatedType = r.relatedType();
		log.relatedId = r.relatedId();
		return log;
	}
}
