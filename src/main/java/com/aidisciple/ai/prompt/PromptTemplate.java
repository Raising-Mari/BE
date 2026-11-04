package com.aidisciple.ai.prompt;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프롬프트 파일 하나. {{변수}}를 채워 AI에 보낼 문자열을 만든다.
 */
public record PromptTemplate(String name, String version, String text) {

	private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

	/** 템플릿에 있는 변수 이름 목록 */
	public Set<String> variableNames() {
		Set<String> names = new LinkedHashSet<>();
		Matcher matcher = VARIABLE.matcher(text);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	/**
	 * 변수를 채운다.
	 * - 템플릿에 있는 변수를 안 넘기거나, 템플릿에 없는 변수를 넘기면 예외 (오타 방지)
	 * - 값 안의 < > 는 &lt; &gt; 로 바꾼다. 학생 글이 구분자(</student_message>)를 닫고 나가는 것을 막는다.
	 * - 한 번만 치환한다. 값 안에 {{...}}가 있어도 다시 치환하지 않는다.
	 */
	public String render(Map<String, String> variables) {
		Set<String> required = variableNames();

		Set<String> missing = new LinkedHashSet<>(required);
		missing.removeAll(variables.keySet());
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(
				"프롬프트 " + name + "." + version + "에 값이 없는 변수: " + missing);
		}

		Set<String> unknown = new LinkedHashSet<>(variables.keySet());
		unknown.removeAll(required);
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException(
				"프롬프트 " + name + "." + version + "에 없는 변수: " + unknown);
		}

		Matcher matcher = VARIABLE.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String value = neutralize(variables.get(matcher.group(1)));
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static String neutralize(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("<", "&lt;").replace(">", "&gt;");
	}
}
