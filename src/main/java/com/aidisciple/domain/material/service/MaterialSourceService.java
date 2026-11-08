package com.aidisciple.domain.material.service;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aidisciple.domain.material.entity.Material;
import com.aidisciple.domain.material.entity.MaterialSentence;
import com.aidisciple.domain.material.entity.MaterialStatus;
import com.aidisciple.domain.material.entity.MaterialUnit;
import com.aidisciple.domain.material.repository.MaterialRepository;
import com.aidisciple.domain.material.repository.MaterialSentenceRepository;
import com.aidisciple.domain.material.repository.MaterialUnitRepository;

/**
 * 다른 도메인(문제 은행)이 자료 문장을 읽는 창구. 엔티티 대신 값만 내보낸다.
 * 문장 내용은 문항 생성·근거 확인에만 쓰고 학생용 응답에는 담지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialSourceService {

	private final MaterialUnitRepository materialUnitRepository;
	private final MaterialRepository materialRepository;
	private final MaterialSentenceRepository sentenceRepository;

	public record UnitSentence(long sentenceId, long materialId, String text) {
	}

	/** 이 주차에 연결된 DONE 자료의 문장 전체. 자료 id 순, 자료 안에서는 seq 순이다. */
	public List<UnitSentence> doneSentencesOfUnit(long unitId) {
		List<Long> materialIds = materialUnitRepository.findByIdUnitId(unitId).stream()
			.map(MaterialUnit::getMaterialId)
			.toList();
		if (materialIds.isEmpty()) {
			return List.of();
		}
		List<UnitSentence> sentences = new ArrayList<>();
		for (Material material : materialRepository.findByIdInAndStatusOrderByIdAsc(materialIds, MaterialStatus.DONE)) {
			for (MaterialSentence sentence : sentenceRepository.findByMaterialIdOrderBySeq(material.getId())) {
				sentences.add(new UnitSentence(sentence.getId(), material.getId(), sentence.getText()));
			}
		}
		return sentences;
	}

	/** 이 자료의 문장 id 전체 (주차 연결이 풀렸을 때 그 문장을 근거로 쓴 문항을 찾는 데 쓴다) */
	public List<Long> sentenceIdsOf(long materialId) {
		return sentenceRepository.findByMaterialIdOrderBySeq(materialId).stream()
			.map(MaterialSentence::getId)
			.toList();
	}

}
