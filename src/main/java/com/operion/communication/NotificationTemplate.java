package com.operion.communication;

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
 * Reusable message shape for system-generated notifications (fee due reminder,
 * attendance alert) - not used by manually-authored Announcements, which carry their
 * own title/body directly. No other module fires one yet; this exists so the seam is
 * in place when e.g. Fees wants to trigger a due-date reminder.
 */
@Getter
@Entity
@Table(name = "notification_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate extends TenantScopedEntity {

	@Column(nullable = false)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	/** Nullable - not every channel needs a subject line. */
	@Column(name = "subject_template")
	private String subjectTemplate;

	@Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
	private String bodyTemplate;

	public NotificationTemplate(String code, NotificationChannel channel, String subjectTemplate, String bodyTemplate) {
		this.code = code;
		this.channel = channel;
		this.subjectTemplate = subjectTemplate;
		this.bodyTemplate = bodyTemplate;
	}
}
