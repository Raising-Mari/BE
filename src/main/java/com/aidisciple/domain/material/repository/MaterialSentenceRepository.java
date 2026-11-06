package com.aidisciple.domain.material.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.material.entity.MaterialSentence;

public interface MaterialSentenceRepository extends JpaRepository<MaterialSentence, Long> {

	long countByMaterialId(Long materialId);

	List<MaterialSentence> findByMaterialIdOrderBySeq(Long materialId);

}
