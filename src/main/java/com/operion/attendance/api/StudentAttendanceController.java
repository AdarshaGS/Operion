package com.operion.attendance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.attendance.AttendanceService;
import com.operion.attendance.AttendanceService.StudentAttendanceMark;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.ClassAttendanceRegister;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.StudentAttendance;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.authorization.RequirePermission;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
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
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final ClassAttendanceRegisterRepository classAttendanceRegisterRepository;
	private final StudentAttendanceRepository studentAttendanceRepository;

	public StudentAttendanceController(AttendanceService attendanceService, SectionRepository sectionRepository,
			StudentEnrollmentRepository studentEnrollmentRepository,
			ClassAttendanceRegisterRepository classAttendanceRegisterRepository,
			StudentAttendanceRepository studentAttendanceRepository) {
		this.attendanceService = attendanceService;
		this.sectionRepository = sectionRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.classAttendanceRegisterRepository = classAttendanceRegisterRepository;
		this.studentAttendanceRepository = studentAttendanceRepository;
	}

	@PostMapping("/sections/{sectionId}/register")
	@RequirePermission("ATTENDANCE_MARK")
	public AttendanceRegisterResponse mark(@PathVariable Long sectionId, @RequestBody MarkAttendanceRequest request) {
		Section section = findSection(sectionId);
		List<StudentAttendanceMark> marks = request.marks().stream()
				.map(entry -> new StudentAttendanceMark(findEnrollment(entry.studentEnrollmentId()),
						AttendanceStatus.valueOf(entry.status()), entry.excused(), entry.remarks()))
				.toList();

		ClassAttendanceRegister register = attendanceService.markDailyAttendance(
				section.getSchoolClass().getAcademicYear(), section, request.attendanceDate(), marks);
		return toRegisterResponse(register);
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
	public StudentAttendanceResponse correct(@PathVariable Long attendanceId, @RequestBody CorrectAttendanceRequest request) {
		StudentAttendance attendance = studentAttendanceRepository.findById(attendanceId)
				.orElseThrow(() -> new IllegalArgumentException("No student attendance with id " + attendanceId));

		StudentAttendance corrected =
				attendanceService.correct(attendance, AttendanceStatus.valueOf(request.newStatus()), request.reason());
		return StudentAttendanceResponse.from(corrected);
	}

	@GetMapping("/enrollments/{enrollmentId}")
	public List<StudentAttendanceResponse> history(@PathVariable Long enrollmentId,
			@RequestParam LocalDate from,
			@RequestParam LocalDate to) {
		return studentAttendanceRepository.findByStudentEnrollmentIdAndAttendanceDateBetween(enrollmentId, from, to)
				.stream()
				.map(StudentAttendanceResponse::from)
				.toList();
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

	private StudentEnrollment findEnrollment(Long enrollmentId) {
		return studentEnrollmentRepository.findById(enrollmentId)
				.orElseThrow(() -> new IllegalArgumentException("No student enrollment with id " + enrollmentId));
	}

	private ClassAttendanceRegister findRegister(Long registerId) {
		return classAttendanceRegisterRepository.findById(registerId)
				.orElseThrow(() -> new IllegalArgumentException("No attendance register with id " + registerId));
	}
}
