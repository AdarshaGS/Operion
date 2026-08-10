package com.operion.examination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
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
 * Extends the standing tenant-isolation proof (OrganisationTenantIsolationTest /
 * StudentTenantIsolationTest / AttendanceTenantIsolationTest / FeeTenantIsolationTest) to
 * the Examination tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExaminationTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private ExamRepository examRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsExams() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "exam-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		AcademicYear yearA = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		examRepository.save(new Exam(yearA, "Term 1", ExamType.UNIT_TEST));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "exam-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		AcademicYear yearB = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		examRepository.save(new Exam(yearB, "Term 1", ExamType.UNIT_TEST));

		TenantContext.set(orgA.getId(), null);
		List<Exam> visibleToA = examRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
	}
}
