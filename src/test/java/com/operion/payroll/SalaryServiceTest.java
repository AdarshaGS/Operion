package com.operion.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.hr.EmploymentType;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Component catalog rules (#222) and effective-dated salary structures (#221). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SalaryServiceTest {

	@Autowired private SalaryComponentRepository componentRepository;
	@Autowired private SalaryStructureRepository structureRepository;
	@Autowired private SalaryStructureLineRepository lineRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private DesignationRepository designationRepository;
	@Autowired private StaffProfileRepository staffProfileRepository;

	private SalaryService salaryService;
	private StaffProfile staff;

	@BeforeEach
	void setUp() {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "pr-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		salaryService = new SalaryService(componentRepository, structureRepository, lineRepository,
				new AuditLogService(auditLogRepository, new ObjectMapper()));

		Person person = personRepository.save(new Person("Asha", "Rao"));
		Designation designation = designationRepository.save(new Designation("Teacher"));
		staff = staffProfileRepository.save(new StaffProfile(person, null, "EMP-1", designation, null,
				LocalDate.of(2025, 6, 1), EmploymentType.PERMANENT));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void componentNamesAreUniqueCaseInsensitivelyAndInactiveOnesCannotJoinNewStructures() {
		SalaryComponent housing = salaryService.createComponent("Housing", ComponentType.EARNING, CalculationBasis.FLAT);
		assertThatThrownBy(() -> salaryService.createComponent(" housing ", ComponentType.EARNING, CalculationBasis.FLAT))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");

		salaryService.changeComponentStatus(housing, SalaryComponentStatus.INACTIVE);
		List<SalaryService.LineInput> lines = List.of(new SalaryService.LineInput(housing, BigDecimal.TEN));
		assertThatThrownBy(() -> salaryService.createStructure(staff, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(1000), lines))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("inactive");
		assertThat(structureRepository.findAll()).isEmpty();
	}

	@Test
	void newStructureMustTakeEffectAfterTheLatestAndHistoryIsPreserved() {
		SalaryComponent pf = salaryService.createComponent("Fund", ComponentType.DEDUCTION, CalculationBasis.PERCENT_OF_BASIC);
		salaryService.createStructure(staff, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(30000),
				List.of(new SalaryService.LineInput(pf, BigDecimal.valueOf(12))));

		assertThatThrownBy(() -> salaryService.createStructure(staff, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(1), List.of()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("after the current one");
		assertThatThrownBy(() -> salaryService.createStructure(staff, LocalDate.of(2025, 1, 1), BigDecimal.valueOf(1), List.of()))
				.isInstanceOf(IllegalStateException.class);

		salaryService.createStructure(staff, LocalDate.of(2026, 4, 1), BigDecimal.valueOf(35000), List.of());

		assertThat(salaryService.historyFor(staff.getId())).extracting(SalaryStructure::getBasicAmount)
				.extracting(BigDecimal::intValue).containsExactly(35000, 30000);
		// The old row is untouched, and the structure in force depends on the date asked about.
		assertThat(salaryService.structureInForce(staff.getId(), LocalDate.of(2025, 12, 1)).orElseThrow().getBasicAmount()
				.intValue()).isEqualTo(30000);
		assertThat(salaryService.structureInForce(staff.getId(), LocalDate.of(2026, 4, 1)).orElseThrow().getBasicAmount()
				.intValue()).isEqualTo(35000);
		assertThat(salaryService.structureInForce(staff.getId(), LocalDate.of(2025, 5, 31))).isEmpty();
	}

	@Test
	void duplicateComponentsAndOutOfRangePercentagesAreRejected() {
		SalaryComponent bonus = salaryService.createComponent("Bonus", ComponentType.EARNING, CalculationBasis.PERCENT_OF_BASIC);

		assertThatThrownBy(() -> salaryService.createStructure(staff, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(1000),
				List.of(new SalaryService.LineInput(bonus, BigDecimal.ONE), new SalaryService.LineInput(bonus, BigDecimal.TEN))))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("twice");
		assertThatThrownBy(() -> salaryService.createStructure(staff, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(1000),
				List.of(new SalaryService.LineInput(bonus, BigDecimal.valueOf(101)))))
				.isInstanceOf(com.operion.common.InvalidRequestException.class);
	}
}
