package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
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
 * Proves FeeService.createFeeStructure enforces "installments must sum to the structure
 * amount" server-side before anything is persisted, per ai-context/erp-system-plan.md §3.2.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeeStructureTest {

	@Autowired
	private FeeService feeService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private FeeStructureInstallmentRepository feeStructureInstallmentRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(FeeStructureGroup feeStructureGroup, FeeCategory feeCategory) {
	}

	private Fixture setUpFixture(String orgSlug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		FeeStructureGroup feeStructureGroup = feeService.createFeeStructureGroup("Grade 5 Annual Fees 2025-26", academicYear, schoolClass);
		FeeCategory feeCategory = feeService.createCategory("TUITION", "Tuition Fee", null);

		return new Fixture(feeStructureGroup, feeCategory);
	}

	@Test
	void createsAStructureWithMatchingInstallmentsAndRejectsAMismatchedSum() {
		Fixture fixture = setUpFixture("fee-structure-school");
		List<InstallmentInput> validInstallments = List.of(
				new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("4000.00")),
				new InstallmentInput(2, LocalDate.of(2025, 10, 15), new BigDecimal("3000.00")),
				new InstallmentInput(3, LocalDate.of(2026, 1, 15), new BigDecimal("3000.00")));

		FeeStructure structure = feeService.createFeeStructure(
				fixture.feeStructureGroup(), fixture.feeCategory(), new BigDecimal("10000.00"), validInstallments);

		List<FeeStructureInstallment> installments = feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(structure.getId());
		assertThat(installments).hasSize(3);
		assertThat(installments.get(0).getAmount()).isEqualByComparingTo("4000.00");

		List<InstallmentInput> mismatchedInstallments = List.of(
				new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("4000.00")),
				new InstallmentInput(2, LocalDate.of(2025, 10, 15), new BigDecimal("3000.00")));

		assertThatThrownBy(() -> feeService.createFeeStructure(
				fixture.feeStructureGroup(), fixture.feeCategory(), new BigDecimal("10000.00"), mismatchedInstallments))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
