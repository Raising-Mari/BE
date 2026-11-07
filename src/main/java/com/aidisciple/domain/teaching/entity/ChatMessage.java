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
 * 가르치기 대화 한 줄. 한 턴 = 학생 메시지 1개 + 제자 메시지 1개이고 둘은 같은 {@code turnNo} 를 가진다.
 * {@code clientMessageId} 는 학생 메시지에만 있다 (중복 전송 방지, DB UNIQUE).
 */
@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseCreatedEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long discipleId;

	@Column(nullable = false)
	private Long unitId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ChatRole role;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false)
	private int turnNo;

	@Column(length = 36, columnDefinition = "CHAR(36)")
	private String clientMessageId;

	@Column(nullable = false)
	private boolean askedWhy;

	private Long aiCallLogId;

	public static ChatMessage student(Long discipleId, Long unitId, int turnNo, String content, String clientMessageId) {
		ChatMessage message = new ChatMessage();
		message.discipleId = discipleId;
		message.unitId = unitId;
		message.role = ChatRole.STUDENT;
		message.turnNo = turnNo;
		message.content = content;
		message.clientMessageId = clientMessageId;
		message.askedWhy = false;
		return message;
	}

	public static ChatMessage disciple(Long discipleId, Long unitId, int turnNo, String content, boolean askedWhy) {
		ChatMessage message = new ChatMessage();
		message.discipleId = discipleId;
		message.unitId = unitId;
		message.role = ChatRole.DISCIPLE;
		message.turnNo = turnNo;
		message.content = content;
		message.askedWhy = askedWhy;
		return message;
	}

}
