package com.operion.messaging;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageThreadRepository extends JpaRepository<MessageThread, Long> {

	Optional<MessageThread> findBySectionIdAndType(Long sectionId, MessageThreadType type);

	/** Every thread a person belongs to - MessagingService.listThreadsForPerson joins this
	 * against ThreadParticipant rather than exposing a participant-keyed finder here, since
	 * "which threads is X in" is inherently a two-table question. */
	@Query("select t from MessageThread t join ThreadParticipant p on p.thread = t where p.person.id = :personId order by t.lastMessageAt desc")
	List<MessageThread> findByParticipantPersonId(@Param("personId") Long personId);

	/** The one DIRECT thread between exactly these two persons, if it already exists -
	 * exactly two participant rows, both matching. getOrCreateDirectThread falls back to
	 * creating one when this comes back empty. */
	@Query("select t from MessageThread t where t.type = com.operion.messaging.MessageThreadType.DIRECT "
			+ "and (select count(p) from ThreadParticipant p where p.thread = t) = 2 "
			+ "and exists (select 1 from ThreadParticipant p where p.thread = t and p.person.id = :personAId) "
			+ "and exists (select 1 from ThreadParticipant p where p.thread = t and p.person.id = :personBId)")
	Optional<MessageThread> findDirectThreadBetween(@Param("personAId") Long personAId, @Param("personBId") Long personBId);
}
