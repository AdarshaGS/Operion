package com.operion.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.operion.academic.TeacherAssignmentRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.identity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates and locks monthly payroll. A run covers every ACTIVE/ON_LEAVE staff member who had
 * joined by month end and has a salary structure in force on the month's last day; staff
 * without one are simply absent from the run. Unpaid-leave/attendance proration is out of
 * scope - an admin adjusts a draft payslip with an explicit adjustment line instead.
 */
@Service
public class PayrollService {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final PayrollRunRepository runRepository;
	private final PayslipRepository payslipRepository;
	private final PayslipLineRepository lineRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final SalaryService salaryService;
	private final AuditLogService auditLogService;

	public PayrollService(PayrollRunRepository runRepository, PayslipRepository payslipRepository,
			PayslipLineRepository lineRepository, StaffProfileRepository staffProfileRepository,
			TeacherAssignmentRepository teacherAssignmentRepository, SalaryService salaryService,
			AuditLogService auditLogService) {
		this.runRepository = runRepository;
		this.payslipRepository = payslipRepository;
		this.lineRepository = lineRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.salaryService = salaryService;
		this.auditLogService = auditLogService;
	}

	public List<PayrollRun> listRuns() {
		return runRepository.findAllByOrderByPayYearDescPayMonthDescIdDesc();
	}

	public List<Payslip> payslipsOf(PayrollRun run) {
		return payslipRepository.findByPayrollRunIdOrderByStaffNameAsc(run.getId());
	}

	public List<PayslipLine> linesOf(Payslip payslip) {
		return lineRepository.findByPayslipIdOrderByIdAsc(payslip.getId());
	}

	public List<Payslip> finalizedPayslipsFor(Long staffProfileId) {
		return payslipRepository
				.findByStaffProfileIdAndPayrollRunStatusOrderByPayrollRunPayYearDescPayrollRunPayMonthDesc(
						staffProfileId, PayrollRunStatus.FINALIZED);
	}

	/**
	 * A first run for a month; refused while any draft or finalized run for it already exists. Every
	 * run-lifecycle method below starts by row-locking the month's runs ({@link #lockPeriod}) and
	 * re-validates against what it read under that lock - the run passed in by a caller was loaded
	 * outside this transaction and can be stale.
	 */
	@Transactional
	public PayrollRun generate(YearMonth period) {
		if (!openRuns(lockPeriod(period)).isEmpty()) {
			throw new IllegalStateException("A payroll run already exists for " + period
					+ " - correct a finalized run via a correction run, or discard the draft first");
		}
		return createRun(period, null);
	}

	/** A correction: a fresh draft for the same month that replaces the finalized run once finalized. */
	@Transactional
	public PayrollRun startCorrection(PayrollRun finalized) {
		List<PayrollRun> locked = lockPeriod(finalized.period());
		PayrollRun current = freshRun(locked, finalized);
		if (current.getStatus() != PayrollRunStatus.FINALIZED) {
			throw new IllegalStateException("Only a finalized payroll run can be corrected");
		}
		if (openRuns(locked).stream().anyMatch(run -> run.getStatus() == PayrollRunStatus.DRAFT)) {
			throw new IllegalStateException("A draft payroll run already exists for " + current.period());
		}
		return createRun(current.period(), current);
	}

	@Transactional
	public Payslip addAdjustment(Payslip payslip, String label, ComponentType type, BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new InvalidRequestException("Adjustment amount must be greater than zero");
		}
		if (amount.stripTrailingZeros().scale() > 2) {
			throw new InvalidRequestException("Adjustment amount cannot have more than 2 decimal places");
		}
		lockPeriod(payslip.getPayrollRun().period());
		requireDraft(payslip);
		lineRepository.save(new PayslipLine(payslip, label, type, PayslipLineSource.ADJUSTMENT, scale(amount)));
		return recompute(payslip);
	}

	@Transactional
	public Payslip removeAdjustment(PayslipLine line) {
		Payslip payslip = line.getPayslip();
		lockPeriod(payslip.getPayrollRun().period());
		requireDraft(payslip);
		if (line.getSource() != PayslipLineSource.ADJUSTMENT) {
			throw new IllegalStateException("Only adjustment lines can be removed from a payslip");
		}
		lineRepository.delete(line);
		lineRepository.flush();
		return recompute(payslip);
	}

	@Transactional
	public void discardDraft(PayrollRun run) {
		PayrollRun current = freshRun(lockPeriod(run.period()), run);
		if (current.getStatus() != PayrollRunStatus.DRAFT) {
			throw new IllegalStateException("Only a draft payroll run can be discarded");
		}
		for (Payslip payslip : payslipsOf(current)) {
			lineRepository.deleteAll(lineRepository.findByPayslipIdOrderByIdAsc(payslip.getId()));
			payslipRepository.delete(payslip);
		}
		payslipRepository.flush();
		runRepository.delete(current);
		auditLogService.record("PayrollRun", current.getId(), "DISCARD", current.getStatus(), null);
	}

	/**
	 * Locks the run. Refuses while any payslip nets negative - fix it with an adjustment first - and
	 * while the month already has a finalized run other than the one this correction replaces.
	 */
	@Transactional
	public PayrollRun finalizeRun(PayrollRun run) {
		List<PayrollRun> locked = lockPeriod(run.period());
		PayrollRun current = freshRun(locked, run);
		if (current.getStatus() != PayrollRunStatus.DRAFT) {
			throw new IllegalStateException("Only a draft payroll run can be finalized");
		}
		PayrollRun superseded = current.getSupersedes();
		if (superseded != null && superseded.getStatus() != PayrollRunStatus.FINALIZED) {
			throw new IllegalStateException("The payroll run being corrected is no longer finalized");
		}
		for (PayrollRun other : locked) {
			boolean replacedByThisRun = superseded != null && other.getId().equals(superseded.getId());
			if (other.getStatus() == PayrollRunStatus.FINALIZED && !replacedByThisRun) {
				throw new IllegalStateException("A finalized payroll run already exists for " + current.period()
						+ " - start a correction run from it instead");
			}
		}
		List<Payslip> payslips = payslipsOf(current);
		if (payslips.isEmpty()) {
			throw new IllegalStateException("The payroll run has no payslips to finalize");
		}
		for (Payslip payslip : payslips) {
			if (payslip.getNetPay().signum() < 0) {
				throw new IllegalStateException("Payslip for " + payslip.getStaffName()
						+ " has negative net pay - adjust it before finalizing");
			}
		}
		current.finalizeRun();
		PayrollRun saved = runRepository.save(current);
		if (superseded != null) {
			superseded.markSuperseded();
			runRepository.save(superseded);
			auditLogService.record("PayrollRun", superseded.getId(), "SUPERSEDED", PayrollRunStatus.FINALIZED,
					PayrollRunStatus.SUPERSEDED);
		}
		auditLogService.record("PayrollRun", saved.getId(), "FINALIZE", PayrollRunStatus.DRAFT,
				PayrollRunStatus.FINALIZED);
		return saved;
	}

	/** Must be the first read of the transaction - see {@link PayrollRunRepository#findPeriodForUpdate}. */
	private List<PayrollRun> lockPeriod(YearMonth period) {
		return runRepository.findPeriodForUpdate(period.getYear(), period.getMonthValue());
	}

	private static List<PayrollRun> openRuns(List<PayrollRun> runs) {
		return runs.stream()
				.filter(run -> run.getStatus() == PayrollRunStatus.DRAFT || run.getStatus() == PayrollRunStatus.FINALIZED)
				.toList();
	}

	/** The run as read under the lock - the instance a caller holds may be stale or already discarded. */
	private static PayrollRun freshRun(List<PayrollRun> locked, PayrollRun stale) {
		return locked.stream().filter(run -> run.getId().equals(stale.getId())).findFirst()
				.orElseThrow(() -> new IllegalStateException("The payroll run no longer exists"));
	}

	/**
	 * A first run covers the current roster. A correction covers exactly the staff of the run it
	 * replaces (someone who has since resigned must keep their payslip) and carries its manual
	 * adjustment lines over.
	 */
	private PayrollRun createRun(YearMonth period, PayrollRun supersedes) {
		PayrollRun run = runRepository.save(new PayrollRun(period, supersedes));
		LocalDate periodEnd = period.atEndOfMonth();
		int payslipCount = 0;
		if (supersedes == null) {
			for (StaffProfile staff : staffProfileRepository
					.findByStatusIn(List.of(StaffProfileStatus.ACTIVE, StaffProfileStatus.ON_LEAVE))) {
				if (staff.getDateOfJoining().isAfter(periodEnd)) {
					continue;
				}
				Optional<SalaryStructure> structure = salaryService.structureInForce(staff.getId(), periodEnd);
				if (structure.isPresent()) {
					buildPayslip(run, staff, structure.get());
					payslipCount++;
				}
			}
		} else {
			for (Payslip original : payslipsOf(supersedes)) {
				StaffProfile staff = original.getStaffProfile();
				Optional<SalaryStructure> structure = salaryService.structureInForce(staff.getId(), periodEnd);
				if (structure.isEmpty()) {
					continue;
				}
				Payslip rebuilt = buildPayslip(run, staff, structure.get());
				for (PayslipLine line : lineRepository.findByPayslipIdOrderByIdAsc(original.getId())) {
					if (line.getSource() == PayslipLineSource.ADJUSTMENT) {
						lineRepository.save(new PayslipLine(rebuilt, line.getLabel(), line.getType(),
								PayslipLineSource.ADJUSTMENT, line.getAmount()));
					}
				}
				recompute(rebuilt);
				payslipCount++;
			}
		}
		auditLogService.record("PayrollRun", run.getId(), "GENERATE", null, period + " (" + payslipCount + " payslips)");
		return run;
	}

	private Payslip buildPayslip(PayrollRun run, StaffProfile staff, SalaryStructure structure) {
		Person person = staff.getPerson();
		String name = (person.getFirstName() + " " + (person.getLastName() == null ? "" : person.getLastName())).trim();
		boolean teacher = !teacherAssignmentRepository.findByTeacherPersonId(person.getId()).isEmpty();
		BigDecimal basic = scale(structure.getBasicAmount());
		Payslip payslip = payslipRepository.save(new Payslip(run, staff, name, teacher, basic));

		lineRepository.save(new PayslipLine(payslip, "Basic", ComponentType.EARNING, PayslipLineSource.BASIC, basic));
		for (SalaryStructureLine line : salaryService.linesOf(structure)) {
			SalaryComponent component = line.getComponent();
			BigDecimal amount = component.getCalculationBasis() == CalculationBasis.PERCENT_OF_BASIC
					? basic.multiply(line.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
					: scale(line.getValue());
			lineRepository.save(new PayslipLine(payslip, component.getName(), component.getType(),
					PayslipLineSource.STRUCTURE, amount));
		}
		return recompute(payslip);
	}

	private Payslip recompute(Payslip payslip) {
		BigDecimal earnings = BigDecimal.ZERO;
		BigDecimal deductions = BigDecimal.ZERO;
		for (PayslipLine line : lineRepository.findByPayslipIdOrderByIdAsc(payslip.getId())) {
			if (line.getType() == ComponentType.EARNING) {
				earnings = earnings.add(line.getAmount());
			} else {
				deductions = deductions.add(line.getAmount());
			}
		}
		payslip.applyTotals(earnings, deductions);
		return payslipRepository.save(payslip);
	}

	private void requireDraft(Payslip payslip) {
		// Re-read: the run reference on a loaded payslip can predate a finalize.
		PayrollRun run = runRepository.findById(payslip.getPayrollRun().getId()).orElseThrow();
		if (run.getStatus() != PayrollRunStatus.DRAFT) {
			throw new IllegalStateException("Payslips of a finalized payroll run are locked");
		}
	}

	private static BigDecimal scale(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}
}
