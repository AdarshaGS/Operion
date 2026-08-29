package com.operion.messaging;

import java.time.Instant;

import com.operion.academic.Section;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The conversation itself - who's in it lives in ThreadParticipant, the messages in
 * Message. lastMessageAt is denormalized (touched by MessagingService.sendMessage on
 * every new Message) purely so the thread list can sort/preview without a join+aggregate
 * over messages on every load.
 */
@Getter
@Entity
@Table(name = "message_threads")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageThread extends TenantScopedEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MessageThreadType type;

	/** Nullable - only set for CLASS_GROUP; a DIRECT thread has no section. */
	@ManyToOne
	@JoinColumn(name = "section_id")
	private Section section;

	@Column(name = "last_message_at")
	private Instant lastMessageAt;

	public MessageThread(MessageThreadType type, Section section) {
		this.type = type;
		this.section = section;
	}

	void touch(Instant sentAt) {
		this.lastMessageAt = sentAt;
	}
}
