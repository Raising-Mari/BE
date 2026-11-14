package com.aidisciple.domain.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.aidisciple.domain.question.logic.SourceSentence;
import com.aidisciple.domain.question.service.QuestionPipelineEngine;

/**
 * 실제 Gemini 로 문항 생성 한 라운드(생성 + 코드 확인 + 누출 시험, AI 2회)를 돌려 단계별 탈락을 본다. 수동 실행.
 * 실행: GEMINI_SMOKE=true ./gradlew test --tests "com.aidisciple.domain.question.QuestionBankSmokeTest" --rerun -i
 * 자료는 직접 쓴 예시다(실제 강의자료 아님). 상식 문장과 이 자료에만 있는 문장을 섞었다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GEMINI_SMOKE", matches = "true")
class QuestionBankSmokeTest {

	private static final List<String> TEXTS = List.of(
		"스택은 나중에 넣은 데이터가 먼저 나오는 후입선출(LIFO) 구조이다.",
		"큐는 먼저 넣은 데이터가 먼저 나오는 선입선출(FIFO) 구조이다.",
		"이 강의에서는 노트를 다시 읽고 빠진 개념을 찾는 과정을 '3단 복기'라고 부른다.",
		"3단 복기의 첫 단계는 틀린 문항의 근거 문장을 찾는 것이다.",
		"3단 복기의 두 번째 단계는 그 근거가 노트에 있었는지 확인하는 것이다.",
		"3단 복기의 마지막 단계는 노트를 고쳐 같은 문항을 다시 푸는 것이다.",
		"실습 과제는 매주 금요일 오후 6시까지 제출하며, 늦으면 하루에 10%씩 감점한다.",
		"중간 프로젝트의 팀 인원은 3명이고, 발표 시간은 팀당 7분이다.");

	@Autowired
	QuestionPipelineEngine engine;

	@Test
	void 실제_Gemini로_한_라운드를_돌려_단계별_탈락_수를_본다() {
		List<SourceSentence> sentences = IntStream.range(0, TEXTS.size())
			.mapToObj(i -> new SourceSentence(i + 1, 1000L + i, TEXTS.get(i)))
			.toList();

		QuestionPipelineEngine.RoundResult result = engine.runRound(
			new QuestionPipelineEngine.RoundInput(0L, "자료구조 예시 주차", sentences, List.of(), 0));

		Map<String, Integer> failures = new TreeMap<>();
		result.outcomes().stream().filter(outcome -> !outcome.passed()).forEach(outcome ->
			outcome.failures().forEach(failure -> failures.merge(failure.name(), 1, Integer::sum)));
		System.out.printf("[SMOKE] 생성 %d, 통과 %d, 탈락 %s%n", result.generatedCount(), result.passed().size(),
			failures);
		result.outcomes().forEach(outcome -> System.out.printf("[SMOKE] %s %s | %s%n",
			outcome.passed() ? "통과" : "탈락(" + outcome.failedStage() + ")", outcome.candidate().gradingMode(),
			outcome.candidate().stem()));

		assertThat(result.generatedCount()).isGreaterThan(0);
	}

}
