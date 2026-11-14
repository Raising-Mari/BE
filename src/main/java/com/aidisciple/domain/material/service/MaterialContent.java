package com.aidisciple.domain.material.service;

/**
 * 처리할 자료의 내용. 원본 파일은 저장하지 않으므로 접수 때 받은 내용을 메모리로 처리 작업에 넘기고, 끝나면 버린다.
 * 그래서 서버가 도중에 재시작되면 이 내용은 사라지고 해당 자료는 {@code FAILED/SERVER_RESTART} 가 된다.
 */
public sealed interface MaterialContent {

	record Pdf(byte[] bytes) implements MaterialContent {
	}

	record Text(String text) implements MaterialContent {
	}

}
