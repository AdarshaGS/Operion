package com.operion.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Proves the bulk import is genuinely per-row (same #28 convention as
 * StudentImportServiceTest): a new grade/class/section can be created in one pass, a
 * repeat of the same section is reported as a duplicate without creating anything, and
 * an unresolvable reference is reported as an error rather than aborting the batch.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, AcademicService.class, ClassSectionRowImportService.class,
		ClassSectionImportService.class, ImportRunService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClassSectionImportServiceTest {

	private static final String HEADER =
			"academicYear,campus,gradeLevel,gradeSequenceOrder,stage,className,sectionName,sectionCapacity,sectionRoom";

	@Autowired
	private ClassSectionImportService classSectionImportService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void createsGradeLevelClassAndSectionOnAValidRow() {
		Organisation organisation = seedOrganisation("iso-class-section-happy");
		academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		campusRepository.save(new Campus("Main Campus", "MAIN"));

		String csv = String.join("\n", HEADER, "2025-2026,Main Campus,Grade 5,5,Primary,Grade 5,A,40,Room 101");
		MockMultipartFile file = new MockMultipartFile("file", "sections.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = classSectionImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(results.get(0).row()).isEqualTo(2);
		assertThat(gradeLevelRepository.findByNameIgnoreCase("Grade 5")).isPresent();
		assertThat(schoolClassRepository.findAll()).extracting(SchoolClass::getDisplayName).containsExactly("Grade 5");
		assertThat(sectionRepository.findAll()).extracting(Section::getName).containsExactly("A");
	}

	@Test
	void reportsAnExistingSectionAsADuplicateAndCreatesNothing() {
		Organisation organisation = seedOrganisation("iso-class-section-duplicate");
		AcademicYear academicYear = academicYearRepository
				.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel gradeLevel = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "Primary"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, gradeLevel, "Grade 5"));
		sectionRepository.save(new Section(schoolClass, "A", 40, "Room 101"));

		String csv = String.join("\n", HEADER, "2025-2026,Main Campus,Grade 5,5,Primary,Grade 5,A,40,Room 101");
		MockMultipartFile file = new MockMultipartFile("file", "sections.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = classSectionImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(sectionRepository.findAll()).hasSize(1);
	}

	@Test
	void reportsAnUnresolvableCampusAsAnError() {
		Organisation organisation = seedOrganisation("iso-class-section-error");
		academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));

		String csv = String.join("\n", HEADER, "2025-2026,Nonexistent Campus,Grade 5,5,Primary,Grade 5,A,40,Room 101");
		MockMultipartFile file = new MockMultipartFile("file", "sections.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = classSectionImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("Nonexistent Campus");
		assertThat(schoolClassRepository.findAll()).isEmpty();
	}

	private Organisation seedOrganisation(String isoCode) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", isoCode));
		TenantContext.set(organisation.getId(), null);
		return organisation;
	}
}
