package com.operion.attendance.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.academic.TeacherAssignmentType;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.authorization.RequirePermission;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only rollup behind the Attendance overview tab. Built from grouped counts, not per-student rows, so a
 * 2,000-student school costs a handful of small queries; the figures match the register and dashboard tiles. */
@RestController
@RequestMapping("/api/v1/attendance/overview")
@RequirePermission("ATTENDANCE_VIEW")
public class AttendanceOverviewController {

	private static final int ACTIVITY_LIMIT = 8;

	private final StudentEnrollmentRepository enrollmentRepository;
	private final StudentAttendanceRepository studentAttendanceRepository;
	private final ClassAttendanceRegisterRepository registerRepository;
	private final StaffAttendanceRepository staffAttendanceRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;

	public AttendanceOverviewController(StudentEnrollmentRepository enrollmentRepository, StudentAttendanceRepository studentAttendanceRepository,
			ClassAttendanceRegisterRepository registerRepository, StaffAttendanceRepository staffAttendanceRepository,
			TeacherAssignmentRepository teacherAssignmentRepository) {
		this.enrollmentRepository = enrollmentRepository;
		this.studentAttendanceRepository = studentAttendanceRepository;
		this.registerRepository = registerRepository;
		this.staffAttendanceRepository = staffAttendanceRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
	}

	private static final class Counts {
		int present;
		int absent;
		int late;
		int marked;
		Instant lastMarkedAt;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public AttendanceOverviewResponse overview(@RequestParam(required = false) LocalDate date) {
		LocalDate day = date != null ? date : LocalDate.now();

		Map<Long, Counts> countsBySection = new HashMap<>();
		for (Object[] row : studentAttendanceRepository.countBySectionAndStatusOn(day)) {
			Counts counts = countsBySection.computeIfAbsent((Long) row[0], key -> new Counts());
			int count = ((Number) row[2]).intValue();
			counts.marked += count;
			switch ((AttendanceStatus) row[1]) {
				case PRESENT -> counts.present += count;
				case ABSENT -> counts.absent += count;
				case LATE -> counts.late += count;
				default -> {
				}
			}
			Instant at = (Instant) row[3];
			if (at != null && (counts.lastMarkedAt == null || at.isAfter(counts.lastMarkedAt))) {
				counts.lastMarkedAt = at;
			}
		}
		Map<Long, String> registerStatusBySection = new HashMap<>();
		for (Object[] row : registerRepository.findSectionStatusesOn(day)) {
			registerStatusBySection.put((Long) row[0], row[1].toString());
		}
		Map<Long, String> homeroomBySection = new HashMap<>();
		for (TeacherAssignment assignment : teacherAssignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE)) {
			if (assignment.getAssignmentType() == TeacherAssignmentType.HOMEROOM) {
				var teacher = assignment.getTeacherPerson();
				homeroomBySection.putIfAbsent(assignment.getSection().getId(), teacher.getFirstName() + " " + teacher.getLastName());
			}
		}

		List<Object[]> sections = new ArrayList<>(enrollmentRepository.countCurrentEnrolledBySection());
		sections.sort(Comparator.comparingInt((Object[] row) -> (Integer) row[4]).thenComparing(row -> (String) row[1]));

		List<AttendanceOverviewResponse.ClassRow> rows = new ArrayList<>();
		List<AttendanceOverviewResponse.Activity> recorded = new ArrayList<>();
		List<AttendanceOverviewResponse.Activity> pending = new ArrayList<>();
		int enrolledTotal = 0;
		int present = 0;
		int absent = 0;
		int late = 0;
		for (Object[] section : sections) {
			Long sectionId = (Long) section[0];
			int enrolled = ((Number) section[5]).intValue();
			String label = section[3] + " - " + section[1];
			Counts counts = countsBySection.getOrDefault(sectionId, new Counts());
			String registerStatus = registerStatusBySection.get(sectionId);
			boolean isRecorded = registerStatus != null;
			int rate = counts.marked == 0 ? 0 : counts.present * 100 / counts.marked;
			rows.add(new AttendanceOverviewResponse.ClassRow(sectionId, (Long) section[2], (String) section[3], (String) section[1], label, homeroomBySection.get(sectionId), enrolled,
					counts.present, counts.absent, counts.late, counts.marked, rate, isRecorded ? "RECORDED" : "PENDING", registerStatus));
			enrolledTotal += enrolled;
			present += counts.present;
			absent += counts.absent;
			late += counts.late;
			if (isRecorded) {
				recorded.add(new AttendanceOverviewResponse.Activity("RECORDED", "Class " + label + " attendance recorded",
						counts.present + " present, " + counts.absent + " absent" + (counts.late > 0 ? ", " + counts.late + " late" : ""), counts.lastMarkedAt));
			} else {
				pending.add(new AttendanceOverviewResponse.Activity("PENDING", "Class " + label + " attendance pending", "Attendance not marked for this day", null));
			}
		}
		recorded.sort(Comparator.comparing(AttendanceOverviewResponse.Activity::at, Comparator.nullsLast(Comparator.reverseOrder())));

		List<AttendanceOverviewResponse.Activity> activity = new ArrayList<>();
		long staffMarked = staffAttendanceRepository.countByAttendanceDate(day);
		if (staffMarked > 0) {
			long staffPresent = staffAttendanceRepository.countByAttendanceDateAndAttendanceStatus(day, AttendanceStatus.PRESENT);
			activity.add(new AttendanceOverviewResponse.Activity("STAFF", "Staff attendance recorded", staffPresent + " of " + staffMarked + " staff present", null));
		}
		activity.addAll(recorded);
		activity.addAll(pending);

		return new AttendanceOverviewResponse(day, enrolledTotal, present, absent, late, pending.size(), rows.size(), rows,
				activity.stream().limit(ACTIVITY_LIMIT).toList());
	}
}
