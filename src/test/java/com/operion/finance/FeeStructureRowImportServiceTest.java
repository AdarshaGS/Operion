package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowStatus;
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

@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, FeeStructureRowImportService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeeStructureRowImportServiceTest {

	@Autowired
	private FeeStructureRowImportService rowImportService;

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
	private FeeStructureRepository feeStructureRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(AcademicYear academicYear, SchoolClass schoolClass) {
	}

	private Fixture setUpFixture(String orgSlug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, "Grade 5"));
		feeService.createFeeStructureGroup("Grade 5 Annual Fees 2025-26", academicYear, schoolClass);
		feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL);

		return new Fixture(academicYear, schoolClass);
	}

	private Map<String, String> row() {
		Map<String, String> row = new HashMap<>();
		row.put("academicYear", "2025-2026");
		row.put("className", "Grade 5");
		row.put("feeCategory", "Tuition Fee");
		row.put("amount", "10000.00");
		row.put("installmentDueDate", "2025-06-15");
		row.put("paymentFrequency", "ONE_SHOT");
		return row;
	}

	@Test
	void happyPathCreatesAFeeStructure() {
		setUpFixture("fee-structure-import-happy");

		var result = rowImportService.importRow(2, row(), false);

		assertThat(result.status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(feeStructureRepository.findAll()).hasSize(1);
	}

	@Test
	void validateOnlyDoesNotPersistAnything() {
		setUpFixture("fee-structure-import-validate");

		var result = rowImportService.importRow(2, row(), true);

		assertThat(result.status()).isEqualTo(ImportRowStatus.VALID);
		assertThat(feeStructureRepository.findAll()).isEmpty();
	}

	@Test
	void reimportingTheSameCategoryReportsADuplicateAndCreatesNothing() {
		setUpFixture("fee-structure-import-duplicate");
		rowImportService.importRow(2, row(), false);

		var result = rowImportService.importRow(3, row(), false);

		assertThat(result.status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(feeStructureRepository.findAll()).hasSize(1);
	}

	@Test
	void unknownClassNameFailsWithAClearMessage() {
		setUpFixture("fee-structure-import-unknown-class");
		Map<String, String> row = row();
		row.put("className", "Grade 99");

		assertThatThrownBy(() -> rowImportService.importRow(2, row, false)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Grade 99");
	}
}
