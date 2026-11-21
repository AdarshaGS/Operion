package com.operion.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.hr.EmploymentType;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
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
	@Autowired private PlatformTransactionManager transactionManager;

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
		payrollService = transactional(new PayrollService(runRepository, payslipRepository, payslipLineRepository,
				staffProfileRepository, teacherAssignmentRepository, salaryService, audit));

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

	/**
	 * The service is built with {@code new}, so wrap it the way Spring does in the running app: every
	 * public method takes the month's pessimistic lock, which needs a transaction, and a refused call
	 * rolls back. (The concurrency the lock guards against is not exercised here.)
	 */
	private <T> T transactional(T service) {
		ProxyFactory proxyFactory = new ProxyFactory(service);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
		@SuppressWarnings("unchecked")
		T proxy = (T) proxyFactory.getProxy();
		return proxy;
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
	void adjustmentsMustBePositiveMoneyWithAtMostTwoDecimals() {
		PayrollRun run = payrollService.generate(MARCH);
		Payslip slip = payrollService.payslipsOf(run).get(0);
		int linesBefore = payrollService.linesOf(slip).size();

		assertThatThrownBy(() -> payrollService.addAdjustment(slip, "Zero", ComponentType.EARNING, BigDecimal.ZERO))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("greater than zero");
		assertThatThrownBy(() -> payrollService.addAdjustment(slip, "Neg", ComponentType.EARNING, BigDecimal.valueOf(-1)))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("greater than zero");
		assertThatThrownBy(() -> payrollService.addAdjustment(slip, "Fine", ComponentType.EARNING, new BigDecimal("10.123")))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("2 decimal places");
		assertThat(payrollService.linesOf(slip)).hasSize(linesBefore);

		// Two decimals (including trailing zeros past the second) are fine.
		payrollService.addAdjustment(slip, "Fine", ComponentType.EARNING, new BigDecimal("10.120"));
		assertThat(payrollService.linesOf(slip)).hasSize(linesBefore + 1);
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

	@Test
	void correctionKeepsStaffWhoHaveSinceLeftAndCarriesTheAdjustmentLines() {
		PayrollRun draft = payrollService.generate(MARCH);
		payrollService.addAdjustment(payrollService.payslipsOf(draft).get(0), "Unpaid leave", ComponentType.DEDUCTION,
				BigDecimal.valueOf(1000));
		PayrollRun first = payrollService.finalizeRun(draft);

		asha.changeStatus(StaffProfileStatus.RESIGNED);
		staffProfileRepository.save(asha);

		PayrollRun correction = payrollService.startCorrection(first);

		List<Payslip> payslips = payrollService.payslipsOf(correction);
		assertThat(payslips).extracting(Payslip::getStaffName).containsExactly("Asha Rao");
		Payslip slip = payslips.get(0);
		assertThat(payrollService.linesOf(slip)).extracting(PayslipLine::getLabel, PayslipLine::getSource)
				.containsExactly(tuple("Basic", PayslipLineSource.BASIC),
						tuple("Housing", PayslipLineSource.STRUCTURE),
						tuple("Fund", PayslipLineSource.STRUCTURE),
						tuple("Unpaid leave", PayslipLineSource.ADJUSTMENT));
		assertThat(slip.getTotalDeductions()).isEqualByComparingTo("4600.00");
		assertThat(slip.getNetPay()).isEqualByComparingTo("30400.00");
	}

	@Test
	void finalizingASecondRunForAMonthThatAlreadyHasAFinalizedOneIsRefused() {
		PayrollRun first = payrollService.finalizeRun(payrollService.generate(MARCH));
		// Not reachable through generate() any more; this is the shape two racing generates would leave behind.
		PayrollRun duplicate = runRepository.save(new PayrollRun(MARCH, null));

		assertThatThrownBy(() -> payrollService.finalizeRun(duplicate)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("finalized payroll run already exists");

		assertThat(runRepository.findById(duplicate.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.DRAFT);
		assertThat(runRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
	}

	@Test
	void staleRunInstancesAreRevalidatedAgainstTheDatabase() {
		PayrollRun staleDraft = payrollService.generate(MARCH);
		PayrollRun first = payrollService.finalizeRun(staleDraft);
		// The caller's copy still says DRAFT although the run is finalized.
		assertThat(staleDraft.getStatus()).isEqualTo(PayrollRunStatus.DRAFT);

		assertThatThrownBy(() -> payrollService.discardDraft(staleDraft)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Only a draft");
		assertThat(runRepository.findById(first.getId())).isPresent();
		assertThat(payslipRepository.findByPayrollRunIdOrderByStaffNameAsc(first.getId())).hasSize(1);
		assertThatThrownBy(() -> payrollService.finalizeRun(staleDraft)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Only a draft");

		PayrollRun correction = payrollService.startCorrection(first);
		payrollService.finalizeRun(correction);
		// `first` still says FINALIZED but is SUPERSEDED now.
		assertThat(first.getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
		assertThatThrownBy(() -> payrollService.startCorrection(first)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Only a finalized");
	}
}
