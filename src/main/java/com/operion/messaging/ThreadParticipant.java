package com.operion.messaging;

import java.time.Instant;

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
 * One row is one Person's membership in one MessageThread - both who's allowed to
 * read/send in it (MessagingService.requireParticipant) and, via lastReadAt, that
 * person's own read position for an unread-count/badge, same "read state is per-person,
 * not per-message" shape as NotificationRecipient rather than a separate read-receipt
 * table per message.
 */
@Getter
@Entity
@Table(name = "thread_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadParticipant extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "thread_id")
	private MessageThread thread;

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	/** Nullable - null means never read. */
	@Column(name = "last_read_at")
	private Instant lastReadAt;

	public ThreadParticipant(MessageThread thread, Person person) {
		this.thread = thread;
		this.person = person;
	}

	void markRead(Instant at) {
		this.lastReadAt = at;
	}
}
