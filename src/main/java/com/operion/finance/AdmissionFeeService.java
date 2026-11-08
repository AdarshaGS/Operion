package com.operion.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.Section;
import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.AcademicYear;
import com.operion.student.AdmissionEnrollmentPort;
import com.operion.student.AdmissionFeeClearanceMode;
import com.operion.student.AdmissionSettings;
import com.operion.student.AdmissionSettingsRepository;
import com.operion.student.FeeClearanceStatus;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrolment with the admission fee attached. Places the student exactly as {@link StudentService#enroll}
 * does, then - when the organisation's admission-fee mode is not OFF and the FEES module is on - assigns
 * every fee structure of the class and year whose category is flagged {@code requiredAtAdmission}, invoices
 * each one's earliest-due instalment as an admission invoice, and sets the enrollment's clearance status.
 * All of it is one transaction: a failure anywhere leaves no enrolment and no invoices.
 *
 * <p>With the mode OFF, the FEES module off, or no flagged structure for the class, nothing is assigned and
 * the status stays NOT_REQUIRED. TRACK and BLOCK behave the same here; BLOCK only differs once a later
 * phase starts rejecting academic writes.
 *
 * <p>Re-enrolment after an exit in the same year revives the closed row, so a structure that is still
 * actively assigned to it is reused rather than assigned again, and so is its admission invoice.
 */
@Service
public class AdmissionFeeService implements AdmissionEnrollmentPort {

	private final StudentService studentService;
	private final FeeService feeService;
	private final AdmissionFeeClearanceService clearanceService;
	private final AdmissionSettingsRepository admissionSettingsRepository;
	private final ModuleAccessService moduleAccessService;
	private final FeeStructureGroupRepository feeStructureGroupRepository;
	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	private final StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	private final InvoiceRepository invoiceRepository;

	public AdmissionFeeService(StudentService studentService, FeeService feeService, AdmissionFeeClearanceService clearanceService,
			AdmissionSettingsRepository admissionSettingsRepository, ModuleAccessService moduleAccessService,
			FeeStructureGroupRepository feeStructureGroupRepository, FeeStructureRepository feeStructureRepository,
			FeeStructureInstallmentRepository feeStructureInstallmentRepository,
			StudentFeeAssignmentRepository studentFeeAssignmentRepository, InvoiceRepository invoiceRepository) {
		this.studentService = studentService;
		this.feeService = feeService;
		this.clearanceService = clearanceService;
		this.admissionSettingsRepository = admissionSettingsRepository;
		this.moduleAccessService = moduleAccessService;
		this.feeStructureGroupRepository = feeStructureGroupRepository;
		this.feeStructureRepository = feeStructureRepository;
		this.feeStructureInstallmentRepository = feeStructureInstallmentRepository;
		this.studentFeeAssignmentRepository = studentFeeAssignmentRepository;
		this.invoiceRepository = invoiceRepository;
	}

	@Override
	@Transactional
	public StudentEnrollment enroll(Student student, AcademicYear academicYear, Section section, Integer rollNumber, LocalDate enrolledDate) {
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, rollNumber, enrolledDate);
		if (!workflowActive()) {
			return enrollment;
		}
		for (FeeStructure structure : requiredStructures(academicYear, section.getSchoolClass())) {
			assignAndInvoice(enrollment, structure);
		}
		clearanceService.applyAtEnrollment(enrollment);
		return enrollment;
	}

	/** What enrolling into this year and class would charge up front - empty while the workflow is inactive. */
	@Transactional(readOnly = true)
	public Preview preview(AcademicYear academicYear, SchoolClass schoolClass) {
		AdmissionFeeClearanceMode mode = currentMode();
		List<PreviewLine> lines = new ArrayList<>();
		if (workflowActive()) {
			for (FeeStructure structure : requiredStructures(academicYear, schoolClass)) {
				FeeStructureInstallment first = earliestInstallment(structure);
				if (first != null) {
					lines.add(new PreviewLine(structure.getId(), structure.getFeeCategory().getName(), structure.getAmount(),
							new FirstInstallment(first.getInstallmentNumber(), first.getDueDate(), first.getAmount())));
				}
			}
		}
		BigDecimal total = lines.stream().map(line -> line.firstInstallment().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Preview(mode.name(), lines, total);
	}

	/** The student-facing view of one enrollment's admission-fee standing. */
	@Transactional(readOnly = true)
	public FeeClearanceView describe(StudentEnrollment enrollment) {
		List<Invoice> invoices = invoiceRepository.findAdmissionInvoices(enrollment.getId());
		FeeClearanceStatus status = enrollment.getFeeClearanceStatus();
		Instant assignedAt = invoices.stream().map(i -> i.getStudentFeeAssignment().getCreatedAt()).min(Comparator.naturalOrder()).orElse(null);
		Instant invoicedAt = invoices.stream().map(Invoice::getCreatedAt).min(Comparator.naturalOrder()).orElse(null);
		boolean settled = status == FeeClearanceStatus.CLEARED || status == FeeClearanceStatus.EXEMPTED;
		List<Step> steps = List.of(
				new Step("ENROLLED", true, enrollment.getCreatedAt()),
				new Step("FEES_ASSIGNED", !invoices.isEmpty(), assignedAt),
				new Step("INVOICES_CREATED", !invoices.isEmpty(), invoicedAt),
				new Step("FIRST_PAYMENT", settled, settled ? enrollment.getFeeClearanceChangedAt() : null));
		List<InvoiceLine> lines = invoices.stream()
				.map(i -> new InvoiceLine(i.getId(), i.getInvoiceNumber(), i.getTotalAmount(), i.getAmountPaid(), i.getOutstanding(), i.getDueDate()))
				.toList();
		OverrideInfo override = enrollment.getFeeClearanceOverrideBy() == null && enrollment.getFeeClearanceOverrideReason() == null ? null
				: new OverrideInfo(enrollment.getFeeClearanceOverrideBy(), enrollment.getFeeClearanceChangedAt(),
						enrollment.getFeeClearanceOverrideReason());
		return new FeeClearanceView(status.name(), currentMode().name(), steps, lines, override);
	}

	private void assignAndInvoice(StudentEnrollment enrollment, FeeStructure structure) {
		StudentFeeAssignment assignment = studentFeeAssignmentRepository
				.findByStudentEnrollmentIdAndStatus(enrollment.getId(), StudentFeeAssignmentStatus.ACTIVE).stream()
				.filter(a -> a.getFeeStructure().getId().equals(structure.getId()))
				.findFirst()
				.orElseGet(() -> feeService.assignFee(enrollment, structure, null, null, null));
		FeeStructureInstallment first = earliestInstallment(structure);
		if (first == null) {
			return;
		}
		if (invoiceRepository.findByStudentFeeAssignmentIdAndFeeStructureInstallmentId(assignment.getId(), first.getId()).isEmpty()) {
			feeService.generateInvoice(assignment, first, true);
		}
	}

	/** Active structures, in id order, of the class and year's active groups whose category is flagged and active. */
	private List<FeeStructure> requiredStructures(AcademicYear academicYear, SchoolClass schoolClass) {
		List<FeeStructure> structures = new ArrayList<>();
		for (FeeStructureGroup group : feeStructureGroupRepository.findByAcademicYearIdAndSchoolClassId(academicYear.getId(), schoolClass.getId())) {
			if (group.getStatus() != FeeStructureGroupStatus.ACTIVE) {
				continue;
			}
			for (FeeStructure structure : feeStructureRepository.findByFeeStructureGroupId(group.getId())) {
				FeeCategory category = structure.getFeeCategory();
				if (structure.getStatus() == FeeStructureStatus.ACTIVE && category.isRequiredAtAdmission()
						&& category.getStatus() == FeeCategoryStatus.ACTIVE) {
					structures.add(structure);
				}
			}
		}
		structures.sort(Comparator.comparing(FeeStructure::getId));
		return structures;
	}

	private FeeStructureInstallment earliestInstallment(FeeStructure structure) {
		return feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(structure.getId()).stream()
				.min(Comparator.comparing(FeeStructureInstallment::getDueDate).thenComparingInt(FeeStructureInstallment::getInstallmentNumber))
				.orElse(null);
	}

	private boolean workflowActive() {
		return currentMode() != AdmissionFeeClearanceMode.OFF
				&& moduleAccessService.isEnabled(TenantContext.getOrganisationId(), AppModule.FEES);
	}

	private AdmissionFeeClearanceMode currentMode() {
		return admissionSettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.map(AdmissionSettings::getFeeClearanceMode)
				.orElse(AdmissionSettings.DEFAULT_FEE_CLEARANCE_MODE);
	}

	public record Preview(String mode, List<PreviewLine> structures, BigDecimal admissionTotal) {
	}

	public record PreviewLine(Long feeStructureId, String categoryName, BigDecimal amount, FirstInstallment firstInstallment) {
	}

	public record FirstInstallment(int number, LocalDate dueDate, BigDecimal amount) {
	}

	public record FeeClearanceView(String status, String mode, List<Step> steps, List<InvoiceLine> invoices, OverrideInfo override) {
	}

	public record Step(String key, boolean done, Instant at) {
	}

	public record InvoiceLine(Long id, String number, BigDecimal total, BigDecimal paid, BigDecimal outstanding, LocalDate dueDate) {
	}

	public record OverrideInfo(Long by, Instant at, String reason) {
	}
}
