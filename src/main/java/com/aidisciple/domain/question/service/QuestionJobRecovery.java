package com.aidisciple.domain.question.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * J-06 중 문제 은행 부분. 서버가 시작될 때 끝나지 못한 작업(QUEUED·RUNNING)을 다시 실행한다.
 * 자료와 달리 필요한 값(문장)이 DB 에 있어서 처음부터 다시 돌릴 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionJobRecovery implements ApplicationRunner {

	private final QuestionJobService jobService;
	private final QuestionJobTrigger trigger;

	@Override
	public void run(ApplicationArguments args) {
		List<Long> jobIds = jobService.requeueInterrupted();
		if (!jobIds.isEmpty()) {
			log.warn("끝나지 못한 문항 생성 작업 {}건을 다시 실행합니다.", jobIds.size());
			jobIds.forEach(trigger::submit);
		}
	}

}
