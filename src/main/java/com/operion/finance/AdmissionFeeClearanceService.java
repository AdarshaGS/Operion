package com.operion.finance;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.student.FeeClearanceStatus;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps StudentEnrollment.feeClearanceStatus in step with the admission invoices. Kept apart from
 * AdmissionFeeService so FeeService can depend on it without a bean cycle (AdmissionFeeService depends
 * on FeeService).
 *
 * <p>The rule: a student is CLEARED once they have at least one admission invoice and every one has
 * nothing outstanding. It checks {@code outstanding <= 0} rather than {@code status == PAID} because a
 * zero-total invoice is created ISSUED. Recalculation only ever moves PENDING to CLEARED - a bounce or
 * refund after clearance does not put the student back, and EXEMPTED never changes - and it is hooked
 * into payments, waivers and adjustments only.
 *
 * <p>Concurrency: callers first lock the enrollment rows ({@link #lockEnrollmentsOf}), then the
 * invoices, so two payments against two admission invoices of one student serialise and the second one
 * reads the first's committed amount instead of a stale snapshot.
 */
@Service
public class AdmissionFeeClearanceService {

	static final int MAX_REASON_LENGTH = 500;

	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final InvoiceRepository invoiceRepository;
	// ObjectProvider for the same reason as FeeService: not every @DataJpaTest slice has an AuditLogService.
	private final ObjectProvider<AuditLogService> auditLogService;

	public AdmissionFeeClearanceService(StudentEnrollmentRepository studentEnrollmentRepository, InvoiceRepository invoiceRepository,
			ObjectProvider<AuditLogService> auditLogService) {
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.invoiceRepository = invoiceRepository;
		this.auditLogService = auditLogService;
	}

	/** Locks, in id order, the enrollments that own any admission invoice among {@code invoiceIds}, and returns their ids. */
	@Transactional
	public List<Long> lockEnrollmentsOf(Collection<Long> invoiceIds) {
		if (invoiceIds.isEmpty()) {
			return List.of();
		}
		List<Long> enrollmentIds = invoiceRepository.findAdmissionEnrollmentIdsByInvoiceIds(invoiceIds).stream().sorted().toList();
		for (Long enrollmentId : enrollmentIds) {
			studentEnrollmentRepository.findByIdForUpdate(enrollmentId);
		}
		return enrollmentIds;
	}

	/** PENDING -> CLEARED where every admission invoice is settled; every other status is left alone. */
	@Transactional
	public void recalculate(Collection<Long> enrollmentIds) {
		for (Long enrollmentId : enrollmentIds.stream().sorted().toList()) {
			StudentEnrollment enrollment = studentEnrollmentRepository.findByIdForUpdate(enrollmentId).orElse(null);
			if (enrollment == null || enrollment.getFeeClearanceStatus() != FeeClearanceStatus.PENDING) {
				continue;
			}
			if (allSettled(invoiceRepository.findAdmissionInvoicesForUpdate(enrollmentId))) {
				move(enrollment, FeeClearanceStatus.CLEARED);
			}
		}
	}

	/**
	 * Called once the admission invoices have been created at enrolment. A NOT_REQUIRED row (a fresh one,
	 * or one revived by a re-enrolment that predates the workflow) becomes PENDING, or CLEARED straight
	 * away when every invoice is already settled (zero-total, or a re-enrolment whose invoices were paid
	 * before the exit). With no admission invoices the status stays as it is. CLEARED and EXEMPTED stay.
	 */
	@Transactional
	public void applyAtEnrollment(StudentEnrollment enrollment) {
		FeeClearanceStatus current = enrollment.getFeeClearanceStatus();
		if (current == FeeClearanceStatus.CLEARED || current == FeeClearanceStatus.EXEMPTED) {
			return;
		}
		studentEnrollmentRepository.findByIdForUpdate(enrollment.getId());
		List<Invoice> invoices = invoiceRepository.findAdmissionInvoicesForUpdate(enrollment.getId());
		if (invoices.isEmpty()) {
			return;
		}
		FeeClearanceStatus target = allSettled(invoices) ? FeeClearanceStatus.CLEARED : FeeClearanceStatus.PENDING;
		if (target != current) {
			move(enrollment, target);
		}
	}

	/**
	 * Manual override: PENDING -> EXEMPTED with a mandatory reason, recorded with who and when, and audited.
	 * Only a PENDING enrollment can be overridden - NOT_REQUIRED and CLEARED have nothing to override, and an
	 * EXEMPTED one already is. Payment activity never changes EXEMPTED afterwards; only {@link #revokeOverride} does.
	 */
	@Transactional
	public StudentEnrollment override(Long enrollmentId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new InvalidRequestException("A reason is required to override the admission fee");
		}
		String trimmed = reason.trim();
		if (trimmed.length() > MAX_REASON_LENGTH) {
			throw new InvalidRequestException("The reason can be at most " + MAX_REASON_LENGTH + " characters");
		}
		StudentEnrollment enrollment = lockedEnrollment(enrollmentId);
		FeeClearanceStatus before = enrollment.getFeeClearanceStatus();
		if (before != FeeClearanceStatus.PENDING) {
			throw new IllegalStateException("Only a pending admission fee can be overridden; this enrollment is " + before);
		}
		enrollment.exemptFromFeeClearance(TenantContext.getActorId(), trimmed);
		studentEnrollmentRepository.save(enrollment);
		auditLogService.ifAvailable(service -> service.record("StudentEnrollment", enrollmentId, "FEE_CLEARANCE_OVERRIDDEN", before,
				overrideAudit(FeeClearanceStatus.EXEMPTED, trimmed)));
		return enrollment;
	}

	/**
	 * Revokes an override: EXEMPTED -> CLEARED when every admission invoice is settled, otherwise PENDING, clearing
	 * the override fields. Only an EXEMPTED enrollment can be revoked. Audited with the reason being withdrawn,
	 * because the row no longer holds it afterwards.
	 */
	@Transactional
	public StudentEnrollment revokeOverride(Long enrollmentId) {
		StudentEnrollment enrollment = lockedEnrollment(enrollmentId);
		FeeClearanceStatus before = enrollment.getFeeClearanceStatus();
		if (before != FeeClearanceStatus.EXEMPTED) {
			throw new IllegalStateException("Only an overridden admission fee can be revoked; this enrollment is " + before);
		}
		String withdrawnReason = enrollment.getFeeClearanceOverrideReason();
		FeeClearanceStatus target = allSettled(invoiceRepository.findAdmissionInvoicesForUpdate(enrollmentId))
				? FeeClearanceStatus.CLEARED : FeeClearanceStatus.PENDING;
		enrollment.revokeFeeClearanceOverride(target);
		studentEnrollmentRepository.save(enrollment);
		auditLogService.ifAvailable(service -> service.record("StudentEnrollment", enrollmentId, "FEE_CLEARANCE_OVERRIDE_REVOKED",
				overrideAudit(before, withdrawnReason), target));
		return enrollment;
	}

	private StudentEnrollment lockedEnrollment(Long enrollmentId) {
		return studentEnrollmentRepository.findByIdForUpdate(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("No enrollment with id " + enrollmentId));
	}

	private static Map<String, Object> overrideAudit(FeeClearanceStatus status, String reason) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("status", status);
		value.put("reason", reason);
		return value;
	}

	static boolean allSettled(List<Invoice> invoices) {
		return !invoices.isEmpty() && invoices.stream().allMatch(invoice -> invoice.getOutstanding().signum() <= 0);
	}

	private void move(StudentEnrollment enrollment, FeeClearanceStatus target) {
		FeeClearanceStatus before = enrollment.getFeeClearanceStatus();
		enrollment.changeFeeClearanceStatus(target);
		studentEnrollmentRepository.save(enrollment);
		auditLogService.ifAvailable(service -> service.record("StudentEnrollment", enrollment.getId(), "FEE_CLEARANCE_CHANGED", before, target));
	}
}
