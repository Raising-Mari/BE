package com.aidisciple.domain.teaching.entity;

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

import com.aidisciple.global.jpa.BaseCreatedEntity;

/**
 * 서버 검증에서 탈락한 AI 의 노트 변경안 (디버깅·발표 자료). 학생에게는 개수만 내려간다.
 * {@code failedChecksJson} 에는 NoteCheckFailure 이름 배열이 들어간다. 예: ["LOW_OVERLAP"]
 */
@Getter
@Entity
@Table(name = "rejected_note_changes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RejectedNoteChange extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long discipleId;

	@Column(nullable = false)
	private Long messageId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private NoteOp op;

	private Long targetEntryId;

	@Column(columnDefinition = "TEXT")
	private String proposedContent;

	@Column(columnDefinition = "TEXT")
	private String proposedEvidence;

	@Column(nullable = false, columnDefinition = "JSON")
	private String failedChecksJson;

	public static RejectedNoteChange create(Long discipleId, Long messageId, NoteOp op, Long targetEntryId,
											String proposedContent, String proposedEvidence, String failedChecksJson) {
		RejectedNoteChange rejected = new RejectedNoteChange();
		rejected.discipleId = discipleId;
		rejected.messageId = messageId;
		rejected.op = op;
		rejected.targetEntryId = targetEntryId;
		rejected.proposedContent = proposedContent;
		rejected.proposedEvidence = proposedEvidence;
		rejected.failedChecksJson = failedChecksJson;
		return rejected;
	}

}
