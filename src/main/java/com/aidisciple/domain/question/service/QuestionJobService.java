package com.aidisciple.domain.question.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import com.aidisciple.domain.course.dto.UnitRef;
import com.aidisciple.domain.course.service.UnitService;
import com.aidisciple.domain.material.service.MaterialSourceService;
import com.aidisciple.domain.question.config.QuestionProperties;
import com.aidisciple.domain.question.entity.GradingMode;
import com.aidisciple.domain.question.entity.JobStatus;
import com.aidisciple.domain.question.entity.JobTriggerType;
import com.aidisciple.domain.question.entity.Question;
import com.aidisciple.domain.question.entity.QuestionEvidence;
import com.aidisciple.domain.question.entity.QuestionGenerationJob;
import com.aidisciple.domain.question.entity.QuestionStatus;
import com.aidisciple.domain.question.entity.QuestionValidationLog;
import com.aidisciple.domain.question.entity.ValidationStage;
import com.aidisciple.domain.question.logic.QuestionCandidate;
import com.aidisciple.domain.question.logic.SourceSentence;
import com.aidisciple.domain.question.repository.QuestionEvidenceRepository;
import com.aidisciple.domain.question.repository.QuestionGenerationJobRepository;
import com.aidisciple.domain.question.repository.QuestionRepository;
import com.aidisciple.domain.question.repository.QuestionValidationLogRepository;

/**
 * 문항 생성 작업(J-02)의 DB 단계. AI 는 부르지 않는다 — {@link QuestionJobRunner} 가 이 서비스와 엔진을 번갈아 부른다.
 * 자료 이벤트(커밋 후)에서 불리는 메서드는 REQUIRES_NEW 다: AFTER_COMMIT 시점에는 원래 트랜잭션이 끝나
 * 그냥 참여하면 저장이 커밋되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionJobService {

	private static final List<JobStatus> OPEN = List.of(JobStatus.QUEUED, JobStatus.RUNNING);
	/** 중복 판정에 쓰는 문항. RETIRED 만 뺀다 — 신고된(UNDER_REVIEW) 문항과 같은 문항도 다시 만들지 않게 */
	private static final List<QuestionStatus> ALIVE = List.of(QuestionStatus.VERIFIED, QuestionStatus.UNJUDGED,
		QuestionStatus.UNDER_REVIEW);
	/** 핵심 요소형에서 맞혀야 하는 요소 비율 (api-spec 11-4 의 0.8) */
	private static final double REQUIRED_ELEMENT_RATIO = 0.8;

	private final QuestionGenerationJobRepository jobRepository;
	private final QuestionRepository questionRepository;
	private final QuestionEvidenceRepository evidenceRepository;
	private final QuestionValidationLogRepository logRepository;
	private final MaterialSourceService materialSourceService;
	private final UnitService unitService;
	private final QuestionProperties properties;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	/** 작업 한 건에 필요한 값. 문장 번호(no)는 주차 전체에서 1부터 새로 붙인다 (자료마다 seq 가 겹치므로). */
	public record JobContext(long jobId, long unitId, long courseId, String unitTitle, int targetCount,
							 List<SourceSentence> sentences, List<String> existingStems) {
	}

	/** 같은 주차에 대기·실행 중인 작업이 있으면 새로 만들지 않는다 (빈 값). 목표 문항 수는 설정값. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Long> queue(long unitId, JobTriggerType trigger, Long requestedBy) {
		return queue(unitId, trigger, requestedBy, properties.targetCount());
	}

	/** 관리자 수동 실행(X-01)처럼 목표 문항 수를 정해서 건다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Long> queue(long unitId, JobTriggerType trigger, Long requestedBy, int targetCount) {
		if (jobRepository.existsByUnitIdAndStatusIn(unitId, OPEN)) {
			return Optional.empty();
		}
		QuestionGenerationJob job = jobRepository.save(
			QuestionGenerationJob.queue(unitId, trigger, requestedBy, targetCount));
		return Optional.of(job.getId());
	}

	/** QUEUED 인 작업만 RUNNING 으로 바꾼다. 이미 누가 시작했거나 끝났으면 false. */
	@Transactional
	public boolean start(long jobId) {
		QuestionGenerationJob job = jobRepository.findById(jobId).orElse(null);
		if (job == null || job.getStatus() != JobStatus.QUEUED) {
			return false;
		}
		job.start(clock.instant());
		return true;
	}

	public Optional<JobContext> context(long jobId) {
		QuestionGenerationJob job = jobRepository.findById(jobId).orElseThrow();
		UnitRef unit = unitService.findRefs(List.of(job.getUnitId())).get(job.getUnitId());
		if (unit == null) {
			return Optional.empty();
		}
		List<MaterialSourceService.UnitSentence> raw = materialSourceService.doneSentencesOfUnit(unit.id());
		List<SourceSentence> sentences = IntStream.range(0, raw.size())
			.mapToObj(i -> new SourceSentence(i + 1, raw.get(i).sentenceId(), raw.get(i).text()))
			.toList();
		List<String> existingStems = questionRepository.findByUnitIdAndStatusIn(unit.id(), ALIVE).stream()
			.map(Question::getStem)
			.toList();
		return Optional.of(new JobContext(jobId, unit.id(), unit.courseId(), unit.title(), job.getTargetCount(),
			sentences, existingStems));
	}

	/**
	 * 한 라운드의 결과를 저장한다. 통과 문항은 UNJUDGED 로(외부 판정 보류, api-spec 11-2) 근거 문장과 함께 저장하고,
	 * 모든 후보는 끝난 단계에 검증 로그를 한 줄씩 남긴다 — 탈락은 탈락한 단계, 통과는 마지막 단계(LEAK_TEST).
	 */
	@Transactional
	public void saveRound(JobContext context, QuestionPipelineEngine.RoundResult result) {
		Map<Integer, SourceSentence> sentencesByNo = context.sentences().stream()
			.collect(Collectors.toMap(SourceSentence::no, Function.identity(), (a, b) -> a));

		for (QuestionPipelineEngine.CandidateOutcome outcome : result.outcomes()) {
			QuestionCandidate candidate = outcome.candidate();
			String candidateJson = toJson(candidate);
			if (!outcome.passed()) {
				logRepository.save(QuestionValidationLog.of(context.jobId(), null, outcome.failedStage(), false,
					candidateJson, toJson(detail(outcome))));
				continue;
			}

			List<SourceSentence> cited = candidate.evidenceSentenceNos().stream()
				.distinct()
				.map(sentencesByNo::get)
				.filter(Objects::nonNull)
				.toList();
			boolean answerList = candidate.gradingMode() == GradingMode.ANSWER_LIST;
			Question question = questionRepository.save(Question.fromAi(context.courseId(), context.unitId(),
				candidate.stem().strip(), candidate.gradingMode(),
				answerList ? toJson(candidate.answers()) : null,
				answerList ? null : toJson(candidate.keyElements()),
				answerList ? null : (int) Math.ceil(candidate.keyElements().size() * REQUIRED_ELEMENT_RATIO),
				candidate.referenceAnswer().strip(),
				cited.stream().map(SourceSentence::text).collect(Collectors.joining(" ")),
				QuestionStatus.UNJUDGED, context.jobId()));
			for (SourceSentence sentence : cited) {
				evidenceRepository.save(QuestionEvidence.of(question.getId(), sentence.sentenceId()));
			}
			logRepository.save(QuestionValidationLog.of(context.jobId(), question.getId(), ValidationStage.LEAK_TEST,
				true, candidateJson, toJson(detail(outcome))));
		}
	}

	@Transactional
	public void finish(long jobId, int generated, int passed, int rejected) {
		jobRepository.findById(jobId).ifPresent(job -> job.finish(clock.instant(), generated, passed, rejected));
	}

	/** 실행 큐가 가득 찬 경우처럼 커밋 후 리스너에서도 불리므로 REQUIRES_NEW 다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(long jobId, String reason) {
		jobRepository.findById(jobId).ifPresent(job -> job.fail(clock.instant(), reason));
	}

	/**
	 * 자료와 주차의 연결이 풀렸다: 그 주차에서 이 자료의 문장을 근거로 쓰던 문항을 RETIRED 로 바꾼다 (api-spec M-06).
	 * 커밋 후 리스너에서 불리므로 REQUIRES_NEW 다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int retireByMaterial(long unitId, long materialId) {
		List<Long> sentenceIds = materialSourceService.sentenceIdsOf(materialId);
		if (sentenceIds.isEmpty()) {
			return 0;
		}
		List<Long> questionIds = evidenceRepository.findQuestionIdsBySentenceIds(sentenceIds);
		if (questionIds.isEmpty()) {
			return 0;
		}
		List<Question> questions = questionRepository.findByIdInAndUnitId(questionIds, unitId).stream()
			.filter(question -> question.getStatus() != QuestionStatus.RETIRED)
			.toList();
		questions.forEach(Question::retire);
		return questions.size();
	}

	/** 서버 시작 시(J-06): 끊긴 RUNNING 은 QUEUED 로 돌리고, 대기 중인 작업 id 를 모두 돌려준다. */
	@Transactional
	public List<Long> requeueInterrupted() {
		List<QuestionGenerationJob> jobs = jobRepository.findByStatusIn(OPEN);
		jobs.stream().filter(job -> job.getStatus() == JobStatus.RUNNING).forEach(QuestionGenerationJob::requeue);
		return jobs.stream().map(QuestionGenerationJob::getId).toList();
	}

	private static Map<String, Object> detail(QuestionPipelineEngine.CandidateOutcome outcome) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("failures", outcome.failures().stream().map(Enum::name).toList());
		if (outcome.leakTestAnswer() != null) {
			detail.put("leakTestAnswer", outcome.leakTestAnswer());
		}
		return detail;
	}

	private String toJson(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

}
