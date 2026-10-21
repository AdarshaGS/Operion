package com.operion.timetable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetablePublicationRepository extends JpaRepository<TimetablePublication, Long> {

	Optional<TimetablePublication> findBySectionId(Long sectionId);

	List<TimetablePublication> findBySectionIdIn(Collection<Long> sectionIds);
}
