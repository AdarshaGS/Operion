package com.operion.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.academic.Section;
import com.operion.audit.AuditLogService;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.Campus;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the daily register lifecycle (DRAFT -> SUBMITTED -> LOCKED), student attendance
 * marking/correction, and staff check-in/out. The two pieces of real business logic:
 * markDailyAttendance rejects re-marking a day that's already open elsewhere or closed,
 * and correct() writes a typed AttendanceCorrection row plus a mirrored AuditLog entry
 * rather than silently mutating history, per ai-context/erp-system-plan.md §3.1.
 */
@Service
public class AttendanceService {

	private final StudentAttendanceRepository studentAttendanceRepository;
	private final ClassAttendanceRegisterRepository classAttendanceRegisterRepository;
	private final AttendanceCorrectionRepository attendanceCorrectionRepository;
	private final StaffAttendanceRepository staffAttendanceRepository;
	private final AuditLogService auditLogService;

	public AttendanceService(StudentAttendanceRepository studentAttendanceRepository,
			ClassAttendanceRegisterRepository classAttendanceRegisterRepository,
			AttendanceCorrectionRepository attendanceCorrectionRepository,
			StaffAttendanceRepository staffAttendanceRepository, AuditLogService auditLogService) {
		this.studentAttendanceRepository = studentAttendanceRepository;
		this.classAttendanceRegisterRepository = classAttendanceRegisterRepository;
		this.attendanceCorrectionRepository = attendanceCorrectionRepository;
		this.staffAttendanceRepository = staffAttendanceRepository;
		this.auditLogService = auditLogService;
	}

	/**
	 * Creates the register (if this is the first mark for the day) and inserts one row
	 * per entry. Rejects entries for a student who already has a row that day - use
	 * correct() to change an already-marked day, never a second insert.
	 */
	@Transactional
	public ClassAttendanceRegister markDailyAttendance(
			AcademicYear academicYear, Section section, LocalDate attendanceDate, List<StudentAttendanceMark> marks) {
		ClassAttendanceRegister register = classAttendanceRegisterRepository
				.findBySectionIdAndAttendanceDate(section.getId(), attendanceDate)
				.orElseGet(() -> classAttendanceRegisterRepository
						.save(new ClassAttendanceRegister(academicYear, section, attendanceDate)));

		if (register.getRegisterStatus() != ClassAttendanceRegisterStatus.DRAFT) {
			throw new IllegalStateException(
					"Register for section " + section.getId() + " on " + attendanceDate + " is not open for marking");
		}

		for (StudentAttendanceMark mark : marks) {
			if (!mark.enrollment().isCurrent()) {
				throw new IllegalStateException(
						"Student enrollment " + mark.enrollment().getId() + " is not a current enrollment");
			}
			studentAttendanceRepository
					.findByStudentEnrollmentIdAndAttendanceDate(mark.enrollment().getId(), attendanceDate)
					.ifPresent(existing -> {
						throw new IllegalStateException("Student enrollment " + mark.enrollment().getId()
								+ " already has attendance marked for " + attendanceDate);
					});
			studentAttendanceRepository.save(new StudentAttendance(mark.enrollment(), academicYear,
					section.getSchoolClass(), section, attendanceDate, mark.status(), mark.excused(), mark.remarks()));
		}
		return register;
	}

	@Transactional
	public ClassAttendanceRegister submitRegister(ClassAttendanceRegister register) {
		register.submit();
		auditLogService.record("ClassAttendanceRegister", register.getId(), "SUBMITTED", null, null);
		return classAttendanceRegisterRepository.save(register);
	}

	@Transactional
	public ClassAttendanceRegister lockRegister(ClassAttendanceRegister register) {
		register.lock();
		auditLogService.record("ClassAttendanceRegister", register.getId(), "LOCKED", null, null);
		return classAttendanceRegisterRepository.save(register);
	}

	@Transactional
	public ClassAttendanceRegister unlockRegister(ClassAttendanceRegister register) {
		register.unlock();
		auditLogService.record("ClassAttendanceRegister", register.getId(), "UNLOCKED", null, null);
		return classAttendanceRegisterRepository.save(register);
	}

	/** Blocked once the day's register is LOCKED - use unlockRegister() first to allow corrections again. */
	@Transactional
	public StudentAttendance correct(StudentAttendance attendance, AttendanceStatus newStatus, String reason) {
		if (!attendance.getStudentEnrollment().isCurrent()) {
			throw new IllegalStateException(
					"Student enrollment " + attendance.getStudentEnrollment().getId() + " is not a current enrollment");
		}
		classAttendanceRegisterRepository
				.findBySectionIdAndAttendanceDate(attendance.getSection().getId(), attendance.getAttendanceDate())
				.filter(register -> register.getRegisterStatus() == ClassAttendanceRegisterStatus.LOCKED)
				.ifPresent(register -> {
					throw new IllegalStateException(
							"Register for section " + attendance.getSection().getId() + " on "
									+ attendance.getAttendanceDate() + " is locked, cannot correct");
				});

		AttendanceStatus previousStatus = attendance.getAttendanceStatus();
		attendanceCorrectionRepository.save(new AttendanceCorrection(attendance, previousStatus, newStatus, reason));
		attendance.correctStatus(newStatus);
		StudentAttendance saved = studentAttendanceRepository.save(attendance);
		auditLogService.record("StudentAttendance", attendance.getId(), "CORRECTED", previousStatus, newStatus);
		return saved;
	}

	public MonthlyAttendanceSummary monthlySummary(Long enrollmentId, int year, int month) {
		LocalDate from = LocalDate.of(year, month, 1);
		LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
		List<StudentAttendance> entries =
				studentAttendanceRepository.findByStudentEnrollmentIdAndAttendanceDateBetween(enrollmentId, from, to);

		int present = 0, absent = 0, late = 0, halfDay = 0, leave = 0;
		for (StudentAttendance entry : entries) {
			switch (entry.getAttendanceStatus()) {
				case PRESENT -> present++;
				case ABSENT -> absent++;
				case LATE -> late++;
				case HALF_DAY -> halfDay++;
				case LEAVE -> leave++;
			}
		}

		int workingDays = entries.size() - leave;
		double percentage = workingDays == 0 ? 0.0
				: Math.round((present + late + 0.5 * halfDay) / workingDays * 1000.0) / 10.0;
		return new MonthlyAttendanceSummary(entries.size(), present, absent, late, halfDay, leave, percentage);
	}

	@Transactional
	public StaffAttendance markStaffAttendance(Person person, Campus campus, LocalDate attendanceDate,
			AttendanceStatus attendanceStatus, Instant checkInTime, String remarks) {
		staffAttendanceRepository.findByPersonIdAndAttendanceDate(person.getId(), attendanceDate).ifPresent(existing -> {
			throw new IllegalStateException(
					"Person " + person.getId() + " already has staff attendance marked for " + attendanceDate);
		});
		return staffAttendanceRepository
				.save(new StaffAttendance(person, campus, attendanceDate, attendanceStatus, checkInTime, remarks));
	}

	@Transactional
	public StaffAttendance checkOutStaff(StaffAttendance attendance, Instant checkOutTime) {
		attendance.checkOut(checkOutTime);
		return staffAttendanceRepository.save(attendance);
	}

	public record StudentAttendanceMark(
			StudentEnrollment enrollment, AttendanceStatus status, boolean excused, String remarks) {
	}
}
