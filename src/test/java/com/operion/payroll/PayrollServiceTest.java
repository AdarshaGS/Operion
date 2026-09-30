package com.operion.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.operion.academic.TeacherAssignmentRepository;
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

/** Payroll run generation, the draft/finalized lock, and correction runs (#223). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayrollServiceTest {

	private static final YearMonth MARCH = YearMonth.of(2026, 3);

	@Autowired private SalaryComponentRepository componentRepository;
	@Autowired private SalaryStructureRepository structureRepository;
	@Autowired private SalaryStructureLineRepository structureLineRepository;
	@Autowired private PayrollRunRepository runRepository;
	@Autowired private PayslipRepository payslipRepository;
	@Autowired private PayslipLineRepository payslipLineRepository;
	@Autowired private TeacherAssignmentRepository teacherAssignmentRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private DesignationRepository designationRepository;
	@Autowired private StaffProfileRepository staffProfileRepository;

	private SalaryService salaryService;
	private PayrollService payrollService;
	private StaffProfile asha;
	private StaffProfile bala;

	@BeforeEach
	void setUp() {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "pp-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		AuditLogService audit = new AuditLogService(auditLogRepository, new ObjectMapper());
		salaryService = new SalaryService(componentRepository, structureRepository, structureLineRepository, audit);
		payrollService = new PayrollService(runRepository, payslipRepository, payslipLineRepository,
				staffProfileRepository, teacherAssignmentRepository, salaryService, audit);

		Designation designation = designationRepository.save(new Designation("Teacher"));
		asha = staffProfileRepository.save(new StaffProfile(personRepository.save(new Person("Asha", "Rao")), null,
				"EMP-1", designation, null, LocalDate.of(2025, 6, 1), EmploymentType.PERMANENT));
		bala = staffProfileRepository.save(new StaffProfile(personRepository.save(new Person("Bala", "Iyer")), null,
				"EMP-2", designation, null, LocalDate.of(2025, 6, 1), EmploymentType.PERMANENT));

		SalaryComponent housing = salaryService.createComponent("Housing", ComponentType.EARNING, CalculationBasis.FLAT);
		SalaryComponent fund = salaryService.createComponent("Fund", ComponentType.DEDUCTION, CalculationBasis.PERCENT_OF_BASIC);
		salaryService.createStructure(asha, LocalDate.of(2025, 6, 1), BigDecimal.valueOf(30000),
				List.of(new SalaryService.LineInput(housing, BigDecimal.valueOf(5000)),
						new SalaryService.LineInput(fund, BigDecimal.valueOf(12))));
		// bala has no structure at all.
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void generateComputesNetFromStructureAndSkipsStaffWithoutOne() {
		PayrollRun run = payrollService.generate(MARCH);

		List<Payslip> payslips = payrollService.payslipsOf(run);
		assertThat(payslips).hasSize(1);
		Payslip slip = payslips.get(0);
		assertThat(slip.getStaffName()).isEqualTo("Asha Rao");
		assertThat(slip.getGrossEarnings()).isEqualByComparingTo("35000.00");
		assertThat(slip.getTotalDeductions()).isEqualByComparingTo("3600.00");
		assertThat(slip.getNetPay()).isEqualByComparingTo("31400.00");
		assertThat(payrollService.linesOf(slip)).extracting(PayslipLine::getLabel)
				.containsExactly("Basic", "Housing", "Fund");
	}

	@Test
	void staffWhoJoinAfterTheMonthEndAreLeftOut() {
		StaffProfile late = staffProfileRepository.save(new StaffProfile(personRepository.save(new Person("Late", "Joiner")),
				null, "EMP-3", asha.getDesignation(), null, LocalDate.of(2026, 4, 5), EmploymentType.PERMANENT));
		salaryService.createStructure(late, LocalDate.of(2026, 4, 5), BigDecimal.valueOf(20000), List.of());

		assertThat(payrollService.payslipsOf(payrollService.generate(MARCH))).extracting(Payslip::getStaffName)
				.containsExactly("Asha Rao");
	}

	@Test
	void secondRunForTheSameMonthIsRefusedUntilTheDraftIsDiscarded() {
		PayrollRun run = payrollService.generate(MARCH);
		assertThatThrownBy(() -> payrollService.generate(MARCH)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already exists");

		payrollService.discardDraft(run);

		assertThat(runRepository.findAll()).isEmpty();
		assertThat(payslipRepository.findAll()).isEmpty();
		assertThat(payslipLineRepository.findAll()).isEmpty();
		assertThat(payrollService.generate(MARCH).getStatus()).isEqualTo(PayrollRunStatus.DRAFT);
	}

	@Test
	void draftAdjustmentsRecomputeTotalsAndFinalizingLocksThePayslip() {
		PayrollRun run = payrollService.generate(MARCH);
		Payslip slip = payrollService.payslipsOf(run).get(0);

		payrollService.addAdjustment(slip, "Unpaid leave", ComponentType.DEDUCTION, BigDecimal.valueOf(1000));
		Payslip reloaded = payslipRepository.findById(slip.getId()).orElseThrow();
		assertThat(reloaded.getNetPay()).isEqualByComparingTo("30400.00");

		PayslipLine adjustment = payrollService.linesOf(reloaded).stream()
				.filter(line -> line.getSource() == PayslipLineSource.ADJUSTMENT).findFirst().orElseThrow();
		payrollService.removeAdjustment(adjustment);
		assertThat(payslipRepository.findById(slip.getId()).orElseThrow().getNetPay()).isEqualByComparingTo("31400.00");

		PayslipLine structureLine = payrollService.linesOf(reloaded).get(1);
		assertThatThrownBy(() -> payrollService.removeAdjustment(structureLine))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("adjustment");

		payrollService.finalizeRun(run);
		assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
		assertThatThrownBy(() -> payrollService.addAdjustment(reloaded, "Late", ComponentType.EARNING, BigDecimal.ONE))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("locked");
		assertThatThrownBy(() -> payrollService.discardDraft(run)).isInstanceOf(IllegalStateException.class);
		assertThat(payrollService.finalizedPayslipsFor(asha.getId())).hasSize(1);
	}

	@Test
	void finalizeRefusesNegativeNetPay() {
		PayrollRun run = payrollService.generate(MARCH);
		Payslip slip = payrollService.payslipsOf(run).get(0);
		payrollService.addAdjustment(slip, "Recovery", ComponentType.DEDUCTION, BigDecimal.valueOf(40000));

		assertThatThrownBy(() -> payrollService.finalizeRun(run)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("negative net pay");
		assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.DRAFT);
	}

	@Test
	void correctionRunSupersedesTheFinalizedOneOnlyWhenItIsFinalized() {
		PayrollRun first = payrollService.finalizeRun(payrollService.generate(MARCH));
		assertThatThrownBy(() -> payrollService.generate(MARCH)).isInstanceOf(IllegalStateException.class);

		PayrollRun correction = payrollService.startCorrection(first);
		assertThatThrownBy(() -> payrollService.startCorrection(first)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("draft");
		assertThat(runRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);

		payrollService.finalizeRun(correction);

		assertThat(runRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.SUPERSEDED);
		assertThat(runRepository.findById(correction.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
		// Only the correction's payslip counts for the staff member now.
		assertThat(payrollService.finalizedPayslipsFor(asha.getId())).hasSize(1);
	}
}
