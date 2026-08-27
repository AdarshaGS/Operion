package com.operion.communication;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

	List<Announcement> findByCampusIdAndStatus(Long campusId, AnnouncementStatus status);

	List<Announcement> findByStatus(AnnouncementStatus status);

	long countByStatusAndPublishedAtAfter(AnnouncementStatus status, Instant publishedAt);
}
