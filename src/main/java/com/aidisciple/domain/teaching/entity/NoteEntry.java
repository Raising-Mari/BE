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

import com.aidisciple.global.jpa.BaseTimeEntity;

/**
 * 제자 노트 한 줄. 내용은 한 번 저장하면 고치지 않는다 — 고쳐 말하면 새 행이 생기고
 * 옛 행은 SUPERSEDED·REMOVED·MERGED 가 된다. 그래서 상태를 바꾸는 메서드만 있고 내용을 바꾸는 메서드는 없다.
 */
@Getter
@Entity
@Table(name = "note_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteEntry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long discipleId;

	@Column(nullable = false)
	private Long unitId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String evidence;

	@Column(nullable = false)
	private Long sourceMessageId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 15)
	private NoteStatus status;

	private Long replacesEntryId;

	private Long mergedIntoEntryId;

	@Column(nullable = false)
	private int version;

	@Column(nullable = false, columnDefinition = "JSON")
	private String verificationDetailJson;

	/** ADD 는 {@code replacesEntryId=null, version=1}, UPDATE 는 옛 노트 id 와 옛 version+1 로 만든다. */
	public static NoteEntry create(Long discipleId, Long unitId, String content, String evidence, Long sourceMessageId,
								   Long replacesEntryId, int version, String verificationDetailJson) {
		NoteEntry note = new NoteEntry();
		note.discipleId = discipleId;
		note.unitId = unitId;
		note.content = content;
		note.evidence = evidence;
		note.sourceMessageId = sourceMessageId;
		note.status = NoteStatus.ACTIVE;
		note.replacesEntryId = replacesEntryId;
		note.version = version;
		note.verificationDetailJson = verificationDetailJson;
		return note;
	}

	public boolean isActive() {
		return status == NoteStatus.ACTIVE;
	}

	/** UPDATE: 새 노트가 이 노트를 대신한다. */
	public void supersede() {
		this.status = NoteStatus.SUPERSEDED;
	}

	/** REMOVE: 학생이 철회했다. */
	public void remove() {
		this.status = NoteStatus.REMOVED;
	}

	/** 자동 병합(≥0.9) 또는 T-05 합치기: 비슷한 새 노트({@code intoEntryId})에 흡수된다. */
	public void mergeInto(long intoEntryId) {
		this.status = NoteStatus.MERGED;
		this.mergedIntoEntryId = intoEntryId;
	}

}
