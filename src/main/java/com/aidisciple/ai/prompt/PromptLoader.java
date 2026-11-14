package com.aidisciple.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * resources/prompts/<이름>.<버전>.txt 를 읽는다. 예: prompts/teaching-turn.v1.txt
 * 버전은 호출하는 쪽이 항상 명시한다. (최신 버전 자동 선택은 하지 않는다 — 로그에 남는 버전이 불분명해지므로)
 */
@Component
public class PromptLoader {

	private static final Pattern NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
	private static final Pattern VERSION = Pattern.compile("v[1-9][0-9]*");

	private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

	public PromptTemplate load(String name, String version) {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("프롬프트 이름 형식이 아닙니다: " + name);
		}
		if (version == null || !VERSION.matcher(version).matches()) {
			throw new IllegalArgumentException("프롬프트 버전 형식이 아닙니다(v1, v2 …): " + version);
		}
		String path = "prompts/" + name + "." + version + ".txt";
		return cache.computeIfAbsent(path, p -> new PromptTemplate(name, version, read(p)));
	}

	private String read(String path) {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("프롬프트 파일이 없습니다: " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("프롬프트 파일을 읽지 못했습니다: " + path, e);
		}
	}
}
