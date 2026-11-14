package com.aidisciple.domain.question.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.aidisciple.ai.AiException;
import com.aidisciple.domain.question.config.QuestionProperties;

/**
 * 문항 생성 작업 하나를 끝까지 돌린다 (J-02). <b>트랜잭션을 걸지 않는다</b>: AI 를 부르는 동안 DB 커넥션을 쥐지 않기 위해서다.
 * 목표 개수를 채우거나 최대 라운드에 닿을 때까지 라운드를 반복한다 (라운드마다 AI 2회).
 * 실패 이유에는 오류 코드나 예외 클래스 이름만 남긴다 (자료 내용이 섞이지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionJobRunner {

	private final QuestionJobService jobService;
	private final QuestionPipelineEngine engine;
	private final QuestionProperties properties;

	public void run(long jobId) {
		if (!jobService.start(jobId)) {
			return;
		}
		try {
			Optional<QuestionJobService.JobContext> found = jobService.context(jobId);
			if (found.isEmpty() || found.get().sentences().isEmpty()) {
				// 연결된 DONE 자료가 없는 주차는 시작하지 않는다 (api-spec 11-2)
				jobService.fail(jobId, "NO_SOURCE_SENTENCES");
				return;
			}
			QuestionJobService.JobContext context = found.get();
			List<String> knownStems = new ArrayList<>(context.existingStems());
			int generated = 0;
			int passed = 0;
			for (int round = 0; round < properties.maxRounds() && passed < context.targetCount(); round++) {
				QuestionPipelineEngine.RoundResult result = engine.runRound(new QuestionPipelineEngine.RoundInput(
					jobId, context.unitTitle(), context.sentences(), List.copyOf(knownStems), round));
				jobService.saveRound(context, result);
				generated += result.generatedCount();
				passed += result.passed().size();
				result.passed().forEach(outcome -> knownStems.add(outcome.candidate().stem()));
				if (result.generatedCount() == 0) {
					break;   // AI 가 더 만들지 못하면 라운드를 더 돌려도 소용없다
				}
			}
			jobService.finish(jobId, generated, passed, generated - passed);
			log.info("문항 생성 작업 완료: jobId={}, generated={}, passed={}", jobId, generated, passed);
		} catch (AiException e) {
			log.warn("문항 생성 작업 실패(AI): jobId={}, code={}", jobId, e.getErrorCode());
			jobService.fail(jobId, e.getErrorCode().name());
		} catch (RuntimeException e) {
			log.warn("문항 생성 작업 실패: jobId={}, cause={}", jobId, e.getClass().getSimpleName());
			jobService.fail(jobId, e.getClass().getSimpleName());
		}
	}

}
