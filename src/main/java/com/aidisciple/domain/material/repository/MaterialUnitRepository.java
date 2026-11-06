package com.aidisciple.domain.material.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aidisciple.domain.material.entity.MaterialUnit;

public interface MaterialUnitRepository extends JpaRepository<MaterialUnit, MaterialUnit.Key> {

	List<MaterialUnit> findByIdMaterialId(Long materialId);

	/** 목록 화면에서 자료마다 따로 조회하지 않도록 한 번에 읽는다. */
	List<MaterialUnit> findByIdMaterialIdIn(Collection<Long> materialIds);

}
