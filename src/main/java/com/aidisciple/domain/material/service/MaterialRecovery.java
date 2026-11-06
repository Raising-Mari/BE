package com.aidisciple.domain.material.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * J-06 중 자료 부분. 서버가 시작될 때 처리가 끝나지 못한 자료({@code PENDING}·{@code PROCESSING})를
 * {@code FAILED/SERVER_RESTART} 로 닫는다. 파일 내용이 메모리에만 있어서 다시 처리할 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialRecovery implements ApplicationRunner {

	private final MaterialStateService stateService;

	@Override
	public void run(ApplicationArguments args) {
		int closed = stateService.failInterrupted();
		if (closed > 0) {
			log.warn("서버 재시작으로 처리가 끝나지 못한 자료 {}건을 실패로 닫았습니다.", closed);
		}
	}

}
