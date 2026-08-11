package com.operion.attendance.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.AttendanceService;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StaffAttendance;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance/staff")
@RequirePermission("STAFF_ATTENDANCE_VIEW")
public class StaffAttendanceController {

	private final AttendanceService attendanceService;
	private final PersonRepository personRepository;
	private final CampusRepository campusRepository;
	private final StaffAttendanceRepository staffAttendanceRepository;

	public StaffAttendanceController(AttendanceService attendanceService, PersonRepository personRepository,
			CampusRepository campusRepository, StaffAttendanceRepository staffAttendanceRepository) {
		this.attendanceService = attendanceService;
		this.personRepository = personRepository;
		this.campusRepository = campusRepository;
		this.staffAttendanceRepository = staffAttendanceRepository;
	}

	@PostMapping
	@RequirePermission("STAFF_ATTENDANCE_MARK")
	public StaffAttendanceResponse mark(@RequestBody CheckInStaffRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		Campus campus = campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));

		StaffAttendance attendance = attendanceService.markStaffAttendance(person, campus, request.attendanceDate(),
				AttendanceStatus.valueOf(request.status()), request.checkInTime(), request.remarks());
		return StaffAttendanceResponse.from(attendance);
	}

	@PatchMapping("/{attendanceId}/check-out")
	@RequirePermission("STAFF_ATTENDANCE_MARK")
	public StaffAttendanceResponse checkOut(@PathVariable Long attendanceId, @RequestBody CheckOutStaffRequest request) {
		StaffAttendance attendance = staffAttendanceRepository.findById(attendanceId)
				.orElseThrow(() -> new IllegalArgumentException("No staff attendance with id " + attendanceId));

		return StaffAttendanceResponse.from(attendanceService.checkOutStaff(attendance, request.checkOutTime()));
	}

	@GetMapping
	public List<StaffAttendanceResponse> list(@RequestParam Long campusId, @RequestParam LocalDate date) {
		return staffAttendanceRepository.findByCampusIdAndAttendanceDate(campusId, date).stream()
				.map(StaffAttendanceResponse::from)
				.toList();
	}
}
