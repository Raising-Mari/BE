package com.aidisciple.domain.question.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.aidisciple.domain.material.event.MaterialProcessedEvent;
import com.aidisciple.domain.material.event.MaterialUnitsChangedEvent;
import com.aidisciple.domain.question.entity.JobTriggerType;

/**
 * 자료 도메인의 이벤트를 받아 문항 생성 작업을 건다. 자료 쪽 트랜잭션이 커밋된 뒤에 받아야 저장된 문장을 읽을 수 있다.
 * 자료 도메인은 문제 은행을 모르고, 이 클래스가 두 도메인을 잇는 유일한 곳이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionJobTrigger {

	private final QuestionJobService jobService;
	private final QuestionJobExecutor executor;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMaterialProcessed(MaterialProcessedEvent event) {
		for (Long unitId : event.unitIds()) {
			queueAndSubmit(unitId, JobTriggerType.MATERIAL_DONE);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMaterialUnitsChanged(MaterialUnitsChangedEvent event) {
		for (Long unitId : event.removedUnitIds()) {
			int retired = jobService.retireByMaterial(unitId, event.materialId());
			if (retired > 0) {
				log.info("주차 연결 해제로 문항 {}개를 RETIRED 로 바꿈: unitId={}, materialId={}", retired, unitId,
					event.materialId());
			}
		}
		// 아직 처리 중인 자료는 처리가 끝날 때 MaterialProcessedEvent 로 작업이 걸린다
		if (event.materialDone()) {
			for (Long unitId : event.addedUnitIds()) {
				queueAndSubmit(unitId, JobTriggerType.UNIT_MAPPED);
			}
		}
	}

	public void queueAndSubmit(long unitId, JobTriggerType trigger) {
		jobService.queue(unitId, trigger, null).ifPresent(this::submit);
	}

	public void submit(long jobId) {
		try {
			executor.runAsync(jobId);
		} catch (TaskRejectedException e) {
			log.warn("작업 큐가 가득 차 문항 생성 작업을 실패로 닫음: jobId={}", jobId);
			jobService.fail(jobId, "QUEUE_FULL");
		}
	}

}
