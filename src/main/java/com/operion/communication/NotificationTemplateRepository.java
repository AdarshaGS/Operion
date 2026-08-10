package com.operion.communication;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

	Optional<NotificationTemplate> findByCode(String code);
}
