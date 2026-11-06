package com.aidisciple.domain.material.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 처리를 별도 스레드({@code taskExecutor})로 넘기는 얇은 껍데기. 비동기 호출은 프록시를 거쳐야 하므로
 * {@link MaterialProcessingService} 와 분리했다. 큐가 가득 차면 호출한 쪽에 {@code TaskRejectedException} 이 난다.
 */
@Component
@RequiredArgsConstructor
public class MaterialProcessor {

	private final MaterialProcessingService processingService;

	@Async("taskExecutor")
	public void processAsync(long materialId, MaterialContent content) {
		processingService.process(materialId, content);
	}

}
