package com.aidisciple.domain.material.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 강의자료. 원본 파일은 저장하지 않고 추출한 문장만 남긴다(저작권·저장 부담). 과목에 속하고, 주차는 나중에 연결한다.
 * 상태는 PENDING → PROCESSING → DONE/FAILED 로만 흐르고 되돌아가지 않는다.
 */
@Getter
@Entity
@Table(name = "materials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long courseId;

	@Column(nullable = false)
	private Long uploaderId;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private MaterialSourceType sourceType;

	@Column(length = 255)
	private String originalFilename;

	private Long fileSizeBytes;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MaterialStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private MaterialFailReason failReason;

	@Column(nullable = false)
	private int charCount;

	@Column(nullable = false)
	private int sentenceCount;

	@Column(nullable = false)
	private Instant consentAgreedAt;

	private Instant processedAt;

	public static Material accept(Long courseId, Long uploaderId, String title, MaterialSourceType sourceType,
		String originalFilename, Long fileSizeBytes, Instant consentAgreedAt) {
		Material material = new Material();
		material.courseId = courseId;
		material.uploaderId = uploaderId;
		material.title = title;
		material.sourceType = sourceType;
		material.originalFilename = originalFilename;
		material.fileSizeBytes = fileSizeBytes;
		material.status = MaterialStatus.PENDING;
		material.consentAgreedAt = consentAgreedAt;
		return material;
	}

	public boolean isOwnedBy(Long userId) {
		return uploaderId.equals(userId);
	}

	/** 접수만 되고 아직 시작하지 않은 자료만 처리를 시작할 수 있다. 그 밖의 상태면 false (중복 실행 방지). */
	public boolean startProcessing() {
		if (status != MaterialStatus.PENDING) {
			return false;
		}
		status = MaterialStatus.PROCESSING;
		return true;
	}

	public void complete(int charCount, int sentenceCount, Instant now) {
		this.status = MaterialStatus.DONE;
		this.failReason = null;
		this.charCount = charCount;
		this.sentenceCount = sentenceCount;
		this.processedAt = now;
	}

	public void fail(MaterialFailReason reason, Instant now) {
		this.status = MaterialStatus.FAILED;
		this.failReason = reason;
		this.processedAt = now;
	}

	/** 처리 큐가 가득 차 접수를 취소한 경우. 사용자는 이 자료의 id 를 받은 적이 없다. */
	public void abandon() {
		this.status = MaterialStatus.DELETED;
	}

	public boolean isVisibleToOwner() {
		return status != MaterialStatus.DELETED;
	}

}
