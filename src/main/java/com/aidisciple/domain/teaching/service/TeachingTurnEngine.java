package com.aidisciple.domain.teaching.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aidisciple.ai.AiException;
import com.aidisciple.ai.AiPurpose;
import com.aidisciple.ai.LlmClient;
import com.aidisciple.ai.LlmRequest;
import com.aidisciple.domain.teaching.config.TeachingProperties;
import com.aidisciple.domain.teaching.entity.ChatRole;
import com.aidisciple.domain.teaching.logic.ActiveNote;
import com.aidisciple.domain.teaching.logic.NoteChangeVerifier;
import com.aidisciple.domain.teaching.logic.ProposedNoteChange;
import com.aidisciple.domain.teaching.logic.VerificationResult;
import com.aidisciple.global.error.ErrorCode;

/**
 * 가르치기 한 턴에서 AI 를 한 번 부르고(api-spec 11-1 의 3~5단계) 노트 변경을 검증한다.
 * DB 를 읽거나 쓰지 않는다. 호출하는 서비스는 AI 호출을 DB 트랜잭션 밖에서 하도록 이 엔진을 트랜잭션 밖에서 부른다.
 */
@Component
public class TeachingTurnEngine {

	static final String PROMPT_NAME = "teaching-turn";
	static final String PROMPT_VERSION = "v1";

	/** 3턴마다 되묻는다. 횟수는 모델에게 맡기지 않고 서버가 센다. */
	private static final int ASK_WHY_EVERY = 3;
	private static final String RELATED_TYPE = "DISCIPLE";

	private final LlmClient llmClient;
	private final NoteChangeVerifier verifier;
	private final TeachingProperties properties;

	public TeachingTurnEngine(LlmClient llmClient, NoteChangeVerifier verifier, TeachingProperties properties) {
		this.llmClient = llmClient;
		this.verifier = verifier;
		this.properties = properties;
	}

	public static boolean shouldAskWhy(int turnNo) {
		return turnNo % ASK_WHY_EVERY == 0;
	}

	public TurnOutcome run(TurnInput input) {
		boolean askWhy = shouldAskWhy(input.turnNo());
		TeachingLlmResponse response = askAi(input, askWhy);

		List<ProposedNoteChange> proposed = response.changes().stream()
			.map(TeachingLlmResponse.LlmNoteChange::toProposed)
			.toList();
		VerificationResult verification = verifier.verify(input.studentMessage(), input.activeNotes(), proposed);
		return new TurnOutcome(response.reply().strip(), askWhy, verification);
	}

	/**
	 * JSON 파싱 실패는 AI 어댑터가 이미 한 번 다시 부른다. 여기서는 JSON 은 맞는데 내용이 비었거나 형식이 틀린 응답만
	 * 한 번 더 요청하고, 그래도 안 되면 AI_BAD_RESPONSE 로 올린다. 이 경우 아무것도 저장하지 않으므로 학생이 같은 메시지로 재시도할 수 있다.
	 */
	private TeachingLlmResponse askAi(TurnInput input, boolean askWhy) {
		LlmRequest request = new LlmRequest(AiPurpose.TEACH_TURN, PROMPT_NAME, PROMPT_VERSION,
			variables(input, askWhy), input.userId(), RELATED_TYPE, input.discipleId());
		for (int attempt = 1; attempt <= 2; attempt++) {
			TeachingLlmResponse response = llmClient.generateJson(request, TeachingLlmResponse.class);
			if (response != null && response.isUsable()) {
				return response;
			}
		}
		throw new AiException(ErrorCode.AI_BAD_RESPONSE);
	}

	private Map<String, String> variables(TurnInput input, boolean askWhy) {
		return Map.of(
			"unit_title", input.unitTitle(),
			"turn_mode", askWhy ? "ASK_WHY" : "NORMAL",
			"notes", formatNotes(input.activeNotes()),
			"conversation", formatConversation(input.recentMessages()),
			"student_message", input.studentMessage());
	}

	/** 최신 노트(id 가 큰 것)부터 글자 수 상한까지만 넣는다. 병합·상한 판단은 이 목록과 무관하게 전체 노트로 한다. */
	private String formatNotes(List<ActiveNote> notes) {
		if (notes.isEmpty()) {
			return "(아직 없음)";
		}
		List<ActiveNote> newestFirst = new ArrayList<>(notes);
		newestFirst.sort((a, b) -> Long.compare(b.id(), a.id()));

		StringBuilder text = new StringBuilder();
		for (ActiveNote note : newestFirst) {
			String line = "[" + note.id() + "] " + note.content();
			if (text.length() + line.length() + 1 > properties.promptNotesMaxChars()) {
				break;
			}
			text.append(line).append('\n');
		}
		return text.isEmpty() ? "(아직 없음)" : text.toString().stripTrailing();
	}

	private static String formatConversation(List<TurnInput.ChatLine> lines) {
		if (lines.isEmpty()) {
			return "(아직 없음)";
		}
		StringBuilder text = new StringBuilder();
		for (TurnInput.ChatLine line : lines) {
			text.append(line.role() == ChatRole.STUDENT ? "학생: " : "제자: ").append(line.content()).append('\n');
		}
		return text.toString().stripTrailing();
	}

}
