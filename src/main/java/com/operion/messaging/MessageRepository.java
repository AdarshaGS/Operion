package com.operion.messaging;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

	/** Full history, oldest first - no pagination yet (same "just return the list" convention
	 * as AnnouncementController.feed); a cursor-based page would be a follow-up once a
	 * thread's history actually gets long enough to matter. */
	List<Message> findByThreadIdOrderByCreatedAtAsc(Long threadId);
}
