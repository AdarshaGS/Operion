package com.operion.communication;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementAudienceMemberRepository extends JpaRepository<AnnouncementAudienceMember, Long> {

	List<AnnouncementAudienceMember> findByAnnouncementId(Long announcementId);
}
