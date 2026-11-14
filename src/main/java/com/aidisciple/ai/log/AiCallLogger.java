package com.aidisciple.ai.log;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 호출 기록을 저장한다.
 * - 별도 트랜잭션: 호출한 쪽이 롤백돼도 "AI를 불렀다"는 기록은 남는다 (무료 한도 집계).
 * - 저장 실패는 삼키고 경고만 남긴다: 기록 실패가 본 기능을 막으면 안 된다.
 */
@Component
public class AiCallLogger {

	private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

	private final AiCallLogRepository repository;
	private final TransactionTemplate newTransaction;

	public AiCallLogger(AiCallLogRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** 저장된 로그 id. 저장에 실패하면 빈 값 (chat_messages.ai_call_log_id 등에 쓴다) */
	public Optional<Long> record(AiCallRecord record) {
		try {
			Long id = newTransaction.execute(status ->
				repository.saveAndFlush(AiCallLog.from(record)).getId());
			return Optional.ofNullable(id);
		} catch (RuntimeException e) {
			log.warn("AI 호출 기록 저장 실패: purpose={}, prompt={}.{}, error={}",
				record.purpose(), record.promptName(), record.promptVersion(), e.toString());
			return Optional.empty();
		}
	}
}
