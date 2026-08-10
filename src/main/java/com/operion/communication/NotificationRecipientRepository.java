package com.operion.communication;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

	List<NotificationRecipient> findByPersonIdOrderByCreatedAtDesc(Long personId);

	List<NotificationRecipient> findByAnnouncementId(Long announcementId);

	Optional<NotificationRecipient> findByIdAndPersonId(Long id, Long personId);
}
