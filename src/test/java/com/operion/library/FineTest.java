package com.operion.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
 * Proves Fine is a standalone ledger entry against a BorrowRecord (not wired into the
 * Fees module, per the design sign-off): multiple fines can exist per record, pay/waive
 * both require PENDING, and each is a one-way settlement.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, LibraryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FineTest {

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
	private FineRepository fineRepository;

	@Autowired
	private LibraryService libraryService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private BorrowRecord setUpBorrowRecord(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Book book = bookRepository.save(new Book(null, "The Pragmatic Programmer", "Hunt & Thomas", null, "Reference", null));
		BookCopy copy = bookCopyRepository.save(new BookCopy(book, campus, "ACC-100", null));
		Person borrower = personRepository.save(new Person("Ira", "Shah"));

		return libraryService.issue(copy, borrower, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
	}

	@Test
	void payingAPendingFineSettlesIt() {
		BorrowRecord record = setUpBorrowRecord("library-fine-pay");
		Fine fine = libraryService.raiseFine(record, new BigDecimal("50.00"), FineReason.OVERDUE);

		libraryService.payFine(fine, LocalDate.of(2026, 1, 20));

		assertThat(fine.getStatus()).isEqualTo(FineStatus.PAID);
		assertThat(fine.getPaidDate()).isEqualTo(LocalDate.of(2026, 1, 20));
	}

	@Test
	void waivingAPendingFineSettlesIt() {
		BorrowRecord record = setUpBorrowRecord("library-fine-waive");
		Fine fine = libraryService.raiseFine(record, new BigDecimal("50.00"), FineReason.OVERDUE);

		libraryService.waiveFine(fine, 42L, "Family hardship");

		assertThat(fine.getStatus()).isEqualTo(FineStatus.WAIVED);
		assertThat(fine.getWaivedBy()).isEqualTo(42L);
		assertThat(fine.getWaivedReason()).isEqualTo("Family hardship");
	}

	@Test
	void payingAnAlreadySettledFineIsRejected() {
		BorrowRecord record = setUpBorrowRecord("library-fine-double-settle");
		Fine fine = libraryService.raiseFine(record, new BigDecimal("50.00"), FineReason.OVERDUE);
		libraryService.payFine(fine, LocalDate.of(2026, 1, 20));

		assertThatThrownBy(() -> libraryService.payFine(fine, LocalDate.of(2026, 1, 21))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void multipleFinesCanExistForTheSameBorrowRecord() {
		BorrowRecord record = setUpBorrowRecord("library-fine-multiple");
		libraryService.raiseFine(record, new BigDecimal("50.00"), FineReason.OVERDUE);
		libraryService.raiseFine(record, new BigDecimal("500.00"), FineReason.DAMAGED);

		List<Fine> fines = fineRepository.findByBorrowRecordId(record.getId());
		assertThat(fines).hasSize(2).extracting(Fine::getReason).containsExactlyInAnyOrder(FineReason.OVERDUE, FineReason.DAMAGED);
	}
}
