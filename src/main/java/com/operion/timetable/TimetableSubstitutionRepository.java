package com.operion.timetable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableSubstitutionRepository extends JpaRepository<TimetableSubstitution, Long> {

	List<TimetableSubstitution> findByDate(LocalDate date);

	List<TimetableSubstitution> findByDateBetween(LocalDate from, LocalDate to);

	Optional<TimetableSubstitution> findByEntryIdAndDate(Long entryId, LocalDate date);

	List<TimetableSubstitution> findBySubstituteTeacherIdAndDate(Long personId, LocalDate date);

	List<TimetableSubstitution> findByEntryId(Long entryId);

	void deleteByEntryId(Long entryId);
}
