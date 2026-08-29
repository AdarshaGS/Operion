package com.operion.messaging;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One chat message. Deliberately minimal for v1 (no edit/delete/attachments, matching
 * the ticket's scope) - sentAt is BaseEntity's inherited createdAt, no separate column.
 */
@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "thread_id")
	private MessageThread thread;

	@ManyToOne(optional = false)
	@JoinColumn(name = "sender_person_id")
	private Person sender;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	public Message(MessageThread thread, Person sender, String body) {
		this.thread = thread;
		this.sender = sender;
		this.body = body;
	}
}
