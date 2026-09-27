package com.operion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves LibraryService's borrow lifecycle: issuing a copy that's already BORROWED is
 * rejected, returning flips the copy back to AVAILABLE, marking a borrowed copy LOST
 * flips both the record and the copy, and a copy freed by return/loss can be issued
 * again.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, LibraryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BorrowRecordLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private BookCopyRepository bookCopyRepository;

	@Autowired
	private LibraryService libraryService;

	@Autowired
	private LibrarySettingsRepository librarySettingsRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(BookCopy copy, Person borrowerA, Person borrowerB) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Book book = bookRepository.save(new Book("978-0-13-468599-1", "Effective Java", "Joshua Bloch", "Addison-Wesley", "Reference", "3rd"));
		BookCopy copy = bookCopyRepository.save(new BookCopy(book, campus, "ACC-001", LocalDate.of(2020, 1, 1), null));

		Person borrowerA = personRepository.save(new Person("Ira", "Shah"));
		Person borrowerB = personRepository.save(new Person("Vikram", "Rao"));

		return new Fixture(copy, borrowerA, borrowerB);
	}

	@Test
	void issuingAvailableCopyMarksItBorrowed() {
		Fixture fixture = setUpFixture("library-issue-basic");

		BorrowRecord record = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

		assertThat(record.getStatus()).isEqualTo(BorrowStatus.BORROWED);
		assertThat(fixture.copy().getStatus()).isEqualTo(BookCopyStatus.BORROWED);
	}

	@Test
	void issuingAnAlreadyBorrowedCopyIsRejected() {
		Fixture fixture = setUpFixture("library-issue-duplicate");
		libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

		assertThatThrownBy(() -> libraryService.issue(fixture.copy(), fixture.borrowerB(), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 16)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void returningACopyFreesItForReissue() {
		Fixture fixture = setUpFixture("library-return-then-reissue");
		BorrowRecord first = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

		libraryService.returnCopy(first, LocalDate.of(2026, 1, 10), false);
		assertThat(first.getStatus()).isEqualTo(BorrowStatus.RETURNED);
		assertThat(fixture.copy().getStatus()).isEqualTo(BookCopyStatus.AVAILABLE);

		BorrowRecord second = libraryService.issue(fixture.copy(), fixture.borrowerB(), LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 25));
		assertThat(second.getStatus()).isEqualTo(BorrowStatus.BORROWED);
	}

	@Test
	void markingACopyLostFlipsBothRecordAndCopy() {
		Fixture fixture = setUpFixture("library-mark-lost");
		BorrowRecord record = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

		libraryService.markLost(record);

		assertThat(record.getStatus()).isEqualTo(BorrowStatus.LOST);
		assertThat(fixture.copy().getStatus()).isEqualTo(BookCopyStatus.LOST);
	}

	@Test
	void returningAnAlreadyReturnedRecordIsRejected() {
		Fixture fixture = setUpFixture("library-double-return");
		BorrowRecord record = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
		libraryService.returnCopy(record, LocalDate.of(2026, 1, 10), false);

		assertThatThrownBy(() -> libraryService.returnCopy(record, LocalDate.of(2026, 1, 11), false)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void returningACopyAsDamagedLeavesItDamagedNotAvailable() {
		Fixture fixture = setUpFixture("library-return-damaged");
		BorrowRecord record = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

		libraryService.returnCopy(record, LocalDate.of(2026, 1, 10), true);

		assertThat(record.getStatus()).isEqualTo(BorrowStatus.RETURNED);
		assertThat(fixture.copy().getStatus()).isEqualTo(BookCopyStatus.DAMAGED);
	}

	@Test
	void markDamagedClosesTheLoanAndLeavesTheCopyDamaged() {
		Fixture fixture = setUpFixture("library-mark-damaged");
		BorrowRecord record = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

		libraryService.markDamaged(record);

		assertThat(record.getStatus()).isEqualTo(BorrowStatus.RETURNED);
		assertThat(fixture.copy().getStatus()).isEqualTo(BookCopyStatus.DAMAGED);
	}

	@Test
	void issueDefaultsDueDateFromConfiguredBorrowingPeriodWhenNotProvided() {
		Fixture fixture = setUpFixture("library-default-due-date");

		BorrowRecord record = libraryService.issue(fixture.copy(), fixture.borrowerA(), LocalDate.of(2026, 1, 1), null);

		assertThat(record.getDueDate()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(LibrarySettings.DEFAULT_BORROWING_PERIOD_DAYS_STUDENT));
	}

	@Test
	void issueRejectsWhenBorrowerIsAtTheirMaxConcurrentLoans() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "library-max-loans"));
		TenantContext.set(organisation.getId(), null);
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person borrower = personRepository.save(new Person("Ira", "Shah"));

		for (int i = 0; i < LibrarySettings.DEFAULT_MAX_LOANS_STUDENT; i++) {
			Book book = bookRepository.save(new Book(null, "Book " + i, null, null, null, null));
			BookCopy copy = bookCopyRepository.save(new BookCopy(book, campus, "ACC-" + i, null, null));
			libraryService.issue(copy, borrower, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
		}

		Book oneMore = bookRepository.save(new Book(null, "One more book", null, null, null, null));
		BookCopy oneMoreCopy = bookCopyRepository.save(new BookCopy(oneMore, campus, "ACC-extra", null, null));

		assertThatThrownBy(() -> libraryService.issue(oneMoreCopy, borrower, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void issueRejectsWhenBorrowerHasAnOverdueBookAndTheRuleIsEnabled() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "library-block-overdue"));
		TenantContext.set(organisation.getId(), null);
		LibrarySettings settings = new LibrarySettings();
		settings.setBlockIssueOnOverdue(true);
		librarySettingsRepository.save(settings);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person borrower = personRepository.save(new Person("Ira", "Shah"));
		Book overdueBook = bookRepository.save(new Book(null, "Overdue Book", null, null, null, null));
		BookCopy overdueCopy = bookCopyRepository.save(new BookCopy(overdueBook, campus, "ACC-overdue", null, null));
		libraryService.issue(overdueCopy, borrower, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 15));

		Book anotherBook = bookRepository.save(new Book(null, "Another Book", null, null, null, null));
		BookCopy anotherCopy = bookCopyRepository.save(new BookCopy(anotherBook, campus, "ACC-another", null, null));

		assertThatThrownBy(() -> libraryService.issue(anotherCopy, borrower, LocalDate.now(), LocalDate.now().plusDays(14)))
				.isInstanceOf(IllegalStateException.class);
	}
}
