package com.aidisciple.domain.question.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 배틀 문항 선택 (api-spec 11-3). 후보를 id 순으로 받아 배틀 id 를 시드로 섞는다 — 같은 후보면 같은 세트가 나온다.
 * 개념이 겹치지 않게 먼저 고르고, 모자라면 겹침을 허용한다. 개념이 없는(null) 문항은 서로 겹치지 않는 것으로 본다
 * (개념 추출을 생략해서 지금은 모두 null 이다, backend-plan D-7).
 */
public final class QuestionPicker {

	private QuestionPicker() {
	}

	public record Candidate(long questionId, Long conceptId) {
	}

	/** 후보가 count 보다 적으면 빈 목록이다 — 호출한 쪽이 BATTLE_NOT_ENOUGH_QUESTIONS 로 처리한다. */
	public static List<Long> pick(List<Candidate> candidatesById, int count, long seed) {
		if (count <= 0 || candidatesById.size() < count) {
			return List.of();
		}
		List<Candidate> shuffled = new ArrayList<>(candidatesById);
		Collections.shuffle(shuffled, new Random(seed));

		List<Long> picked = new ArrayList<>();
		Set<Long> usedConcepts = new HashSet<>();
		List<Candidate> sameConcept = new ArrayList<>();
		for (Candidate candidate : shuffled) {
			if (picked.size() == count) {
				break;
			}
			if (candidate.conceptId() == null || usedConcepts.add(candidate.conceptId())) {
				picked.add(candidate.questionId());
			} else {
				sameConcept.add(candidate);
			}
		}
		for (Candidate candidate : sameConcept) {
			if (picked.size() == count) {
				break;
			}
			picked.add(candidate.questionId());
		}
		return picked;
	}

}
