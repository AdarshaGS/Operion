package com.operion.communication;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.Campus;
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
 * One row targets exactly one audience - no multi-audience fan-out at authoring time,
 * same "one row is one target" choice as ExamSchedule. audienceRefId resolves against
 * SchoolClass/Section/Student depending on audienceType; ORG/CAMPUS need no ref since
 * campusId (nullable = org-wide) already carries that scope. Publishing snapshots the
 * audience into NotificationRecipient rows once - later audience changes never
 * retroactively alter who already got notified, per the design sign-off.
 */
@Getter
@Entity
@Table(name = "announcements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement extends TenantScopedEntity {

	/** Nullable - null means org-wide. */
	@ManyToOne
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(name = "audience_type", nullable = false, length = 20)
	private AudienceType audienceType;

	/** Nullable - unused for ORG/CAMPUS, see class doc. */
	@Column(name = "audience_ref_id")
	private Long audienceRefId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnnouncementStatus status;

	@Column(name = "published_at")
	private Instant publishedAt;

	public Announcement(Campus campus, String title, String body, AudienceType audienceType, Long audienceRefId) {
		this.campus = campus;
		this.title = title;
		this.body = body;
		this.audienceType = audienceType;
		this.audienceRefId = audienceRefId;
		this.status = AnnouncementStatus.DRAFT;
	}

	/** Publishing is a one-way trip for v1 - see class doc; not re-publishable after cancel either. */
	public void publish() {
		if (status != AnnouncementStatus.DRAFT) {
			throw new IllegalStateException("Only a draft announcement can be published, was " + status);
		}
		this.status = AnnouncementStatus.PUBLISHED;
		this.publishedAt = Instant.now();
	}

	/** Only a draft can be cancelled - retracting an already-fanned-out publish is a v2 concern. */
	public void cancel() {
		if (status != AnnouncementStatus.DRAFT) {
			throw new IllegalStateException("Only a draft announcement can be cancelled, was " + status);
		}
		this.status = AnnouncementStatus.CANCELLED;
	}
}
