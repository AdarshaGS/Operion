package com.operion.communication;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row is one Person picked into a SELECTED_GROUP announcement's ad-hoc audience -
 * see AudienceType's class doc for why this doesn't fit the single audienceRefId column
 * every other audience type uses. Written once at announcement creation, read by
 * CommunicationService.resolveAudience at publish time.
 */
@Getter
@Entity
@Table(name = "announcement_audience_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementAudienceMember extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "announcement_id")
	private Announcement announcement;

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	public AnnouncementAudienceMember(Announcement announcement, Person person) {
		this.announcement = announcement;
		this.person = person;
	}
}
