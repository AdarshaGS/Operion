package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

/** The counter row is created (in its own transaction) before the locking read, never inside it - see createIfMissing. */
class StudentIdGeneratorTest {

	private final StudentIdCounterRepository repository = mock(StudentIdCounterRepository.class);
	private final StudentIdGenerator generator = new StudentIdGenerator(repository);

	@Test
	void aMissingCounterIsCreatedOnceBeforeTheLockedRead() {
		when(repository.existsByYear(2026)).thenReturn(false);
		when(repository.findByYear(2026)).thenReturn(Optional.of(new StudentIdCounter(2026)));

		String id = generator.next(LocalDate.of(2026, 6, 1));

		assertThat(id).isEqualTo("STU-2026-00001");
		verify(repository).createIfMissing(2026);
	}

	@Test
	void anExistingCounterIsNotCreatedAgain() {
		when(repository.existsByYear(2026)).thenReturn(true);
		when(repository.findByYear(2026)).thenReturn(Optional.of(new StudentIdCounter(2026)));

		generator.next(LocalDate.of(2026, 6, 1));

		verify(repository, never()).createIfMissing(any(Integer.class));
	}

	@Test
	void aLostCreationRaceIsIgnoredAndTheWinnersRowIsLocked() {
		when(repository.existsByYear(2026)).thenReturn(false);
		org.mockito.Mockito.doThrow(new CannotAcquireLockException("deadlock")).when(repository).createIfMissing(2026);
		when(repository.findByYear(2026)).thenReturn(Optional.of(new StudentIdCounter(2026)));

		assertThat(generator.next(LocalDate.of(2026, 6, 1))).startsWith("STU-2026-");
	}
}
