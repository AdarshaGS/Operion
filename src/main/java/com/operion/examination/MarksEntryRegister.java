package com.operion.examination;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per ExamSchedule (DRAFT -> SUBMITTED -> APPROVED), mirroring
 * ClassAttendanceRegister's DRAFT -> SUBMITTED -> LOCKED lifecycle. Marks can only be
 * entered while DRAFT; ExaminationService.publishReportCard() refuses to publish off a
 * schedule whose register isn't APPROVED. Created lazily on first marks entry, same
 * "no upfront placeholder rows" convention as MarksEntry itself. No submitted_by/
 * approved_by columns - each transition also writes to the shared AuditLog via
 * ExaminationService, per ai-context/erp-system-plan.md §3.1 (same reasoning as
 * ClassAttendanceRegister). Per #134.
 */
@Getter
@Entity
@Table(name = "marks_entry_registers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarksEntryRegister extends TenantScopedEntity {

	@OneToOne(optional = false)
	@JoinColumn(name = "exam_schedule_id", unique = true)
	private ExamSchedule examSchedule;

	@Enumerated(EnumType.STRING)
	@Column(name = "register_status", nullable = false, length = 20)
	private MarksEntryRegisterStatus registerStatus;

	public MarksEntryRegister(ExamSchedule examSchedule) {
		this.examSchedule = examSchedule;
		this.registerStatus = MarksEntryRegisterStatus.DRAFT;
	}

	public void submit() {
		if (registerStatus != MarksEntryRegisterStatus.DRAFT) {
			throw new IllegalStateException("Marks entry register " + getId() + " is not DRAFT, cannot submit");
		}
		this.registerStatus = MarksEntryRegisterStatus.SUBMITTED;
	}

	public void approve() {
		if (registerStatus != MarksEntryRegisterStatus.SUBMITTED) {
			throw new IllegalStateException("Marks entry register " + getId() + " is not SUBMITTED, cannot approve");
		}
		this.registerStatus = MarksEntryRegisterStatus.APPROVED;
	}
}
