package com.aidisciple.domain.material.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.aidisciple.global.jpa.BaseCreatedEntity;

/**
 * 자료와 주차의 연결(다대다). 연결을 풀면 행을 지운다. 자료 전체가 연결된 모든 주차의 출처가 된다(쪽·문장 범위 지정 없음).
 */
@Getter
@Entity
@Table(name = "material_units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialUnit extends BaseCreatedEntity {

	@EmbeddedId
	private Key id;

	public static MaterialUnit link(Long materialId, Long unitId) {
		MaterialUnit link = new MaterialUnit();
		link.id = new Key(materialId, unitId);
		return link;
	}

	public Long getMaterialId() {
		return id.materialId;
	}

	public Long getUnitId() {
		return id.unitId;
	}

	@Getter
	@Embeddable
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@EqualsAndHashCode
	public static class Key implements Serializable {

		@Column(name = "material_id", nullable = false)
		private Long materialId;

		@Column(name = "unit_id", nullable = false)
		private Long unitId;

		public Key(Long materialId, Long unitId) {
			this.materialId = materialId;
			this.unitId = unitId;
		}

	}

}
