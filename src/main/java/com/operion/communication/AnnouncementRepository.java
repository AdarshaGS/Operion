package com.operion.communication;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

	List<Announcement> findByCampusIdAndStatus(Long campusId, AnnouncementStatus status);

	List<Announcement> findByStatus(AnnouncementStatus status);

	long countByStatusAndPublishedAtAfter(AnnouncementStatus status, Instant publishedAt);

	/** Due-for-auto-publish query for ScheduledAnnouncementPublisher - run once per tenant
	 * under that tenant's TenantContext, since @TenantId scopes this like every other query. */
	List<Announcement> findByStatusAndScheduledAtLessThanEqual(AnnouncementStatus status, Instant now);
}
