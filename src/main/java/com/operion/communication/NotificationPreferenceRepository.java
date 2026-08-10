package com.operion.communication;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

	List<NotificationPreference> findByPersonId(Long personId);

	Optional<NotificationPreference> findByPersonIdAndChannel(Long personId, NotificationChannel channel);
}
