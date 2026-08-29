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
 * template-fired system notification has no Announcement behind it. subject/body are
 * captured here (rather than re-derived from Announcement/NotificationTemplate at
 * dispatch time) so NotificationDispatchService's async, later-running dispatch has
 * content to send without needing to reload the source it came from.
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

	/** Nullable - not every channel/source needs a subject line (SMS never does). */
	private String subject;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "read_at")
	private Instant readAt;

	/** Nullable - only set for a FAILED row. */
	@Column(name = "failure_reason")
	private String failureReason;

	/** Nullable - only set once an EMAIL/SMS row reaches SENT. Which sender accepted it
	 * ("brevo", "resend", ...), paired with providerMessageId to correlate a later
	 * NotificationDeliveryWebhookService callback back to this exact row. */
	private String provider;

	@Column(name = "provider_message_id")
	private String providerMessageId;

	/** IN_APP fan-out lands directly on SENT (row creation is delivery for that channel);
	 * EMAIL/SMS start PENDING and wait for NotificationDispatchService to actually try a
	 * provider and call markSent/markFailed - see DeliveryStatus's class doc. */
	public NotificationRecipient(Announcement announcement, Person person, NotificationChannel channel, String subject, String body) {
		this.announcement = announcement;
		this.person = person;
		this.channel = channel;
		this.subject = subject;
		this.body = body;
		if (channel == NotificationChannel.IN_APP) {
			this.deliveryStatus = DeliveryStatus.SENT;
			this.sentAt = Instant.now();
		} else {
			this.deliveryStatus = DeliveryStatus.PENDING;
		}
	}

	public void markRead() {
		if (deliveryStatus != DeliveryStatus.SENT && deliveryStatus != DeliveryStatus.DELIVERED) {
			throw new IllegalStateException("Only a sent or delivered notification can be marked read, was " + deliveryStatus);
		}
		this.deliveryStatus = DeliveryStatus.READ;
		this.readAt = Instant.now();
	}

	/** Called by NotificationDispatchService once a provider actually accepts a PENDING
	 * EMAIL/SMS row. provider/providerMessageId are what NotificationDeliveryWebhookService
	 * later matches a delivery-confirmation callback against, via
	 * NotificationRecipientRepository's cross-tenant lookup. */
	public void markSent(String provider, String providerMessageId) {
		if (deliveryStatus != DeliveryStatus.PENDING) {
			throw new IllegalStateException("Only a pending notification can be marked sent, was " + deliveryStatus);
		}
		this.deliveryStatus = DeliveryStatus.SENT;
		this.sentAt = Instant.now();
		this.provider = provider;
		this.providerMessageId = providerMessageId;
	}

	/** Called by NotificationDispatchService when every configured provider for this channel rejected/failed the send. */
	public void markFailed(String reason) {
		if (deliveryStatus != DeliveryStatus.PENDING) {
			throw new IllegalStateException("Only a pending notification can be marked failed, was " + deliveryStatus);
		}
		this.deliveryStatus = DeliveryStatus.FAILED;
		this.failureReason = reason;
	}

	/** Called by NotificationDeliveryWebhookService once the provider's own callback
	 * confirms the recipient's inbox/handset actually got it - distinct from markSent,
	 * which only means the gateway accepted the send. A duplicate delivery webhook for an
	 * already-DELIVERED row is expected (providers can redeliver events) and is the
	 * caller's job to tolerate, not this method's - it always throws on a non-SENT row. */
	public void markDelivered() {
		if (deliveryStatus != DeliveryStatus.SENT) {
			throw new IllegalStateException("Only a sent notification can be marked delivered, was " + deliveryStatus);
		}
		this.deliveryStatus = DeliveryStatus.DELIVERED;
		this.deliveredAt = Instant.now();
	}
}
