package com.operion.email;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Insert-then-update outbox row, one per send attempt - same "record what was actually
 * sent, when, and via what" pattern as com.operion.communication.NotificationRecipient,
 * kept as a separate table rather than reused directly since that one is shaped around
 * Announcement/Person audience fan-out (a School-vertical concept per
 * ai-context/platform-boundaries.md) while this is generic transactional email
 * (org member invites, email verification) with no Person/Announcement to hang off.
 */
@Getter
@Entity
@Table(name = "email_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOutbox extends TenantScopedEntity {

	@Column(nullable = false)
	private String recipient;

	@Column(nullable = false)
	private String subject;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EmailDeliveryStatus status;

	/** Which of brevo/resend actually delivered it - null until SENT. */
	private String provider;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "failure_reason")
	private String failureReason;

	public EmailOutbox(String recipient, String subject) {
		this.recipient = recipient;
		this.subject = subject;
		this.status = EmailDeliveryStatus.PENDING;
	}

	public void markSent(String provider) {
		this.status = EmailDeliveryStatus.SENT;
		this.provider = provider;
		this.sentAt = Instant.now();
	}

	public void markFailed(String reason) {
		this.status = EmailDeliveryStatus.FAILED;
		this.failureReason = reason;
	}
}
