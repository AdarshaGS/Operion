package com.operion.communication;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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
 * The fan-out + delivery outbox row: one per (announcement-or-template-firing) x
 * recipient x channel, created once at publish/fire time (a snapshot, not resolved live
 * on every read - see Announcement's class doc). announcementId is nullable because a
 * template-fired system notification has no Announcement behind it.
 */
@Getter
@Entity
@Table(name = "notification_recipients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRecipient extends TenantScopedEntity {

	/** Nullable - null for a template-fired system notification. */
	@ManyToOne
	@JoinColumn(name = "announcement_id")
	private Announcement announcement;

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_status", nullable = false, length = 20)
	private DeliveryStatus deliveryStatus;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "read_at")
	private Instant readAt;

	/** Nullable - only set for a FAILED row. */
	@Column(name = "failure_reason")
	private String failureReason;

	/** IN_APP fan-out lands directly on SENT - row creation is delivery for that channel, see class doc. */
	public NotificationRecipient(Announcement announcement, Person person, NotificationChannel channel) {
		this.announcement = announcement;
		this.person = person;
		this.channel = channel;
		this.deliveryStatus = DeliveryStatus.SENT;
		this.sentAt = Instant.now();
	}

	public void markRead() {
		if (deliveryStatus != DeliveryStatus.SENT) {
			throw new IllegalStateException("Only a sent notification can be marked read, was " + deliveryStatus);
		}
		this.deliveryStatus = DeliveryStatus.READ;
		this.readAt = Instant.now();
	}
}
