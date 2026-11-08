package com.aidisciple.domain.grading.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 채점을 별도 스레드(taskExecutor)로 넘기는 얇은 껍데기. 비동기 호출은 프록시를 거쳐야 해서 Runner 와 분리했다
 * (배틀 풀이의 BattleSolveExecutor 와 같은 방식). 큐가 가득 차면 호출한 쪽에 TaskRejectedException 이 난다.
 */
@Component
@RequiredArgsConstructor
public class GradingExecutor {

	private final GradingRunner runner;

	@Async("taskExecutor")
	public void runAsync(long battleId) {
		runner.run(battleId);
	}

}
