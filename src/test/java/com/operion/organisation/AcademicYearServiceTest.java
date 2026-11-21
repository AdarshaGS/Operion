package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Proves AcademicYearService's one-current-at-a-time invariant (#108): marking a new
 * year current un-sets (not closes) whichever year was current before. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, AcademicYearService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AcademicYearServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private AcademicYearService academicYearService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void markingANewYearCurrentUnsetsThePreviousOneWithoutClosingIt() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "academic-year-service-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear yearOne = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear yearTwo = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));

		academicYearService.markCurrent(yearOne.getId());
		AcademicYear reloadedOne = academicYearRepository.findById(yearOne.getId()).orElseThrow();
		assertThat(reloadedOne.isCurrent()).isTrue();
		assertThat(reloadedOne.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);

		academicYearService.markCurrent(yearTwo.getId());

		AcademicYear reloadedOneAgain = academicYearRepository.findById(yearOne.getId()).orElseThrow();
		assertThat(reloadedOneAgain.isCurrent()).isFalse();
		assertThat(reloadedOneAgain.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);

		AcademicYear reloadedTwo = academicYearRepository.findById(yearTwo.getId()).orElseThrow();
		assertThat(reloadedTwo.isCurrent()).isTrue();
		assertThat(academicYearRepository.findByCurrentTrue()).map(AcademicYear::getId).contains(yearTwo.getId());
	}

	@Test
	void closingAYearUnsetsCurrentAndMarksItClosed() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "academic-year-close-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear year = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		academicYearService.markCurrent(year.getId());

		academicYearService.close(year.getId());

		AcademicYear reloaded = academicYearRepository.findById(year.getId()).orElseThrow();
		assertThat(reloaded.isCurrent()).isFalse();
		assertThat(reloaded.getStatus()).isEqualTo(AcademicYearStatus.CLOSED);
	}
	@Test
	void switchingTheCurrentYearBackAndForthNeverTripsTheOneCurrentKey() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "academic-year-switch-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear yearOne = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear yearTwo = academicYearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));

		academicYearService.markCurrent(yearOne.getId());
		academicYearService.markCurrent(yearTwo.getId());
		academicYearService.markCurrent(yearOne.getId());
		academicYearService.markCurrent(yearTwo.getId());

		assertThat(academicYearRepository.findAll()).filteredOn(AcademicYear::isCurrent).extracting(AcademicYear::getId)
				.containsExactly(yearTwo.getId());
		assertThat(academicYearRepository.findById(yearOne.getId()).orElseThrow().getCurrentMarker()).isNull();
		assertThat(academicYearRepository.findById(yearTwo.getId()).orElseThrow().getCurrentMarker()).isTrue();
	}

	@Test
	void theDatabaseRefusesASecondCurrentYearInTheSameOrganisation() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "academic-year-unique-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear yearOne = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear yearTwo = academicYearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		academicYearService.markCurrent(yearOne.getId());

		// Straight through the repository, bypassing AcademicYearService's un-set: only the unique key stands in the way.
		yearTwo.markCurrent();
		assertThatThrownBy(() -> academicYearRepository.saveAndFlush(yearTwo)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void differentOrganisationsEachKeepTheirOwnCurrentYear() {
		Organisation orgA = organisationRepository.save(new Organisation("School A", "Trust A", "academic-year-org-a"));
		Organisation orgB = organisationRepository.save(new Organisation("School B", "Trust B", "academic-year-org-b"));
		TenantContext.set(orgA.getId(), null);
		AcademicYear yearA = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		academicYearService.markCurrent(yearA.getId());
		TenantContext.set(orgB.getId(), null);
		AcademicYear yearB = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));

		academicYearService.markCurrent(yearB.getId());

		assertThat(academicYearRepository.findByCurrentTrue()).map(AcademicYear::getId).contains(yearB.getId());
	}
}
