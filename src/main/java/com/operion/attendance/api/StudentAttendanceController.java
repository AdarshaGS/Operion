package com.operion.attendance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.academic.WorkingCalendarEntryType;
import com.operion.attendance.AttendanceAlertService;
import com.operion.attendance.AttendanceCorrectionRepository;
import com.operion.attendance.AttendanceService;
import com.operion.attendance.AttendanceService.StudentAttendanceMark;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.ClassAttendanceRegister;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.StudentAttendance;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.EnumParam;
import com.operion.common.InvalidRequestException;
import com.operion.organisation.AcademicYear;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
@RequirePermission("ATTENDANCE_VIEW")
public class StudentAttendanceController {

	private final AttendanceService attendanceService;
	private final AttendanceAlertService attendanceAlertService;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final ClassAttendanceRegisterRepository classAttendanceRegisterRepository;
	private final StudentAttendanceRepository studentAttendanceRepository;
	private final AttendanceCorrectionRepository attendanceCorrectionRepository;
	private final WorkingCalendarEntryRepository workingCalendarEntryRepository;

	public StudentAttendanceController(AttendanceService attendanceService, AttendanceAlertService attendanceAlertService,
			SectionRepository sectionRepository,
			StudentEnrollmentRepository studentEnrollmentRepository,
			ClassAttendanceRegisterRepository classAttendanceRegisterRepository,
			StudentAttendanceRepository studentAttendanceRepository,
			AttendanceCorrectionRepository attendanceCorrectionRepository,
			WorkingCalendarEntryRepository workingCalendarEntryRepository) {
		this.attendanceService = attendanceService;
		this.attendanceAlertService = attendanceAlertService;
		this.sectionRepository = sectionRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.classAttendanceRegisterRepository = classAttendanceRegisterRepository;
		this.studentAttendanceRepository = studentAttendanceRepository;
		this.attendanceCorrectionRepository = attendanceCorrectionRepository;
		this.workingCalendarEntryRepository = workingCalendarEntryRepository;
	}

	@PostMapping("/sections/{sectionId}/register")
	@RequirePermission("ATTENDANCE_MARK")
	public AttendanceRegisterResponse mark(@PathVariable Long sectionId, @Valid @RequestBody MarkAttendanceRequest request) {
		Section section = findSection(sectionId);
		requireMarkableDay(section, request.attendanceDate());
		List<StudentAttendanceMark> marks = request.marks().stream()
				.map(entry -> new StudentAttendanceMark(
						findEnrollmentOfSection(entry.studentEnrollmentId(), section, request.attendanceDate()),
						EnumParam.parse(AttendanceStatus.class, entry.status()), entry.excused(), entry.remarks()))
				.toList();

		ClassAttendanceRegister register = attendanceService.markDailyAttendance(
				section.getSchoolClass().getAcademicYear(), section, request.attendanceDate(), marks);
		List<StudentAttendance> entries =
				studentAttendanceRepository.findBySectionIdAndAttendanceDate(section.getId(), request.attendanceDate());
		entries.stream()
				.filter(entry -> entry.getAttendanceStatus() == AttendanceStatus.ABSENT)
				.forEach(attendanceAlertService::notifyAbsence);
		return AttendanceRegisterResponse.of(register, entries);
	}

	@PostMapping("/register/{registerId}/submit")
	@RequirePermission("ATTENDANCE_MARK")
	public AttendanceRegisterResponse submit(@PathVariable Long registerId) {
		return toRegisterResponse(attendanceService.submitRegister(findRegister(registerId)));
	}

	@PostMapping("/register/{registerId}/lock")
	@RequirePermission("ATTENDANCE_LOCK")
	public AttendanceRegisterResponse lock(@PathVariable Long registerId) {
		return toRegisterResponse(attendanceService.lockRegister(findRegister(registerId)));
	}

	@PostMapping("/register/{registerId}/unlock")
	@RequirePermission("ATTENDANCE_UNLOCK")
	public AttendanceRegisterResponse unlock(@PathVariable Long registerId) {
		return toRegisterResponse(attendanceService.unlockRegister(findRegister(registerId)));
	}

	@GetMapping("/sections/{sectionId}/register")
	public AttendanceRegisterResponse getRegister(
			@PathVariable Long sectionId, @RequestParam LocalDate date) {
		ClassAttendanceRegister register = classAttendanceRegisterRepository
				.findBySectionIdAndAttendanceDate(sectionId, date)
				.orElseThrow(() -> new IllegalArgumentException(
						"No attendance register for section " + sectionId + " on " + date));
		return toRegisterResponse(register);
	}

	@PatchMapping("/students/{attendanceId}")
	@RequirePermission("ATTENDANCE_CORRECT")
	public StudentAttendanceResponse correct(@PathVariable Long attendanceId, @Valid @RequestBody CorrectAttendanceRequest request) {
		StudentAttendance attendance = studentAttendanceRepository.findById(attendanceId)
				.orElseThrow(() -> new IllegalArgumentException("No student attendance with id " + attendanceId));

		AttendanceStatus newStatus = EnumParam.parse(AttendanceStatus.class, request.newStatus());
		StudentAttendance corrected = attendanceService.correct(attendance, newStatus, request.reason());
		if (newStatus == AttendanceStatus.ABSENT) {
			attendanceAlertService.notifyAbsence(corrected);
		}
		return StudentAttendanceResponse.from(corrected);
	}

	@GetMapping("/students/{attendanceId}/corrections")
	public List<AttendanceCorrectionResponse> corrections(@PathVariable Long attendanceId) {
		if (!studentAttendanceRepository.existsById(attendanceId)) {
			throw new IllegalArgumentException("No student attendance with id " + attendanceId);
		}
		return attendanceCorrectionRepository.findByStudentAttendanceId(attendanceId).stream()
				.map(AttendanceCorrectionResponse::from)
				.toList();
	}

	@GetMapping("/enrollments/{enrollmentId}")
	public List<StudentAttendanceResponse> history(@PathVariable Long enrollmentId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to) {
		requireEnrollment(enrollmentId);
		if (from.isAfter(to)) {
			throw new InvalidRequestException("'from' must not be after 'to'");
		}
		return studentAttendanceRepository.findByStudentEnrollmentIdAndAttendanceDateBetween(enrollmentId, from, to)
				.stream()
				.map(StudentAttendanceResponse::from)
				.toList();
	}

	@GetMapping("/enrollments/{enrollmentId}/summary")
	public MonthlyAttendanceSummaryResponse summary(
			@PathVariable Long enrollmentId, @RequestParam int year, @RequestParam int month) {
		requireEnrollment(enrollmentId);
		if (month < 1 || month > 12) {
			throw new InvalidRequestException("month must be between 1 and 12");
		}
		if (year < 1 || year > 9999) {
			throw new InvalidRequestException("year must be between 1 and 9999");
		}
		return MonthlyAttendanceSummaryResponse.from(attendanceService.monthlySummary(enrollmentId, year, month));
	}

	private AttendanceRegisterResponse toRegisterResponse(ClassAttendanceRegister register) {
		List<StudentAttendance> entries =
				studentAttendanceRepository.findBySectionIdAndAttendanceDate(register.getSection().getId(), register.getAttendanceDate());
		return AttendanceRegisterResponse.of(register, entries);
	}

	private Section findSection(Long sectionId) {
		return sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
	}

	/** A day is markable when it falls in the section's academic year and is not a holiday for its campus. */
	private void requireMarkableDay(Section section, LocalDate date) {
		AcademicYear year = section.getSchoolClass().getAcademicYear();
		if (date.isBefore(year.getStartDate()) || date.isAfter(year.getEndDate())) {
			throw new InvalidRequestException("Attendance date " + date + " is outside the academic year " + year.getName());
		}
		Long campusId = section.getSchoolClass().getCampus().getId();
		boolean holiday = workingCalendarEntryRepository.existsByAcademicYearIdAndDateAndTypeAndCampusIsNull(
				year.getId(), date, WorkingCalendarEntryType.HOLIDAY)
				|| workingCalendarEntryRepository.existsByAcademicYearIdAndDateAndTypeAndCampusId(
						year.getId(), date, WorkingCalendarEntryType.HOLIDAY, campusId);
		if (holiday) {
			throw new InvalidRequestException("Attendance cannot be marked on " + date + ": it is a holiday in the working calendar");
		}
	}

	private StudentEnrollment findEnrollmentOfSection(Long enrollmentId, Section section, LocalDate date) {
		StudentEnrollment enrollment = findEnrollment(enrollmentId);
		if (!enrollment.getSection().getId().equals(section.getId())) {
			throw new InvalidRequestException(
					"Student enrollment " + enrollmentId + " does not belong to section " + section.getId());
		}
		if (enrollment.getEnrolledDate() != null && date.isBefore(enrollment.getEnrolledDate())) {
			throw new InvalidRequestException("Student enrollment " + enrollmentId + " starts on "
					+ enrollment.getEnrolledDate() + ", so " + date + " cannot be marked");
		}
		return enrollment;
	}

	private void requireEnrollment(Long enrollmentId) {
		if (!studentEnrollmentRepository.existsById(enrollmentId)) {
			throw new IllegalArgumentException("No student enrollment with id " + enrollmentId);
		}
	}

	private StudentEnrollment findEnrollment(Long enrollmentId) {
		return studentEnrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + enrollmentId));
	}

	private ClassAttendanceRegister findRegister(Long registerId) {
		return classAttendanceRegisterRepository.findById(registerId)
				.orElseThrow(() -> new IllegalArgumentException("No attendance register with id " + registerId));
	}
}
