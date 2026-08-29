package com.operion.messaging;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ThreadParticipantRepository extends JpaRepository<ThreadParticipant, Long> {

	List<ThreadParticipant> findByThreadId(Long threadId);

	Optional<ThreadParticipant> findByThreadIdAndPersonId(Long threadId, Long personId);

	boolean existsByThreadIdAndPersonId(Long threadId, Long personId);
}
