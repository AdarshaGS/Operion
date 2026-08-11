package com.operion.hr.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.hr.HrService;
import com.operion.hr.LeaveRequest;
import com.operion.hr.LeaveRequestRepository;
import com.operion.hr.LeaveRequestStatus;
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/leave-requests")
@RequirePermission("HR_VIEW")
public class LeaveRequestController {

	private final HrService hrService;
	private final LeaveRequestRepository leaveRequestRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final LeaveTypeRepository leaveTypeRepository;
	private final AcademicYearRepository academicYearRepository;

	public LeaveRequestController(HrService hrService, LeaveRequestRepository leaveRequestRepository,
			StaffProfileRepository staffProfileRepository, LeaveTypeRepository leaveTypeRepository,
			AcademicYearRepository academicYearRepository) {
		this.hrService = hrService;
		this.leaveRequestRepository = leaveRequestRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.leaveTypeRepository = leaveTypeRepository;
		this.academicYearRepository = academicYearRepository;
	}

	@PostMapping
	@RequirePermission("HR_LEAVE_MANAGE")
	public LeaveRequestResponse raise(@RequestBody RaiseLeaveRequestRequest request) {
		StaffProfile staffProfile = findStaffProfile(request.staffProfileId());
		LeaveType leaveType = findLeaveType(request.leaveTypeId());
		AcademicYear academicYear = findAcademicYear(request.academicYearId());
		LeaveRequest leaveRequest = hrService.raiseLeaveRequest(
				staffProfile, leaveType, academicYear, request.startDate(), request.endDate(), request.numberOfDays(), request.reason());
		return LeaveRequestResponse.from(leaveRequest);
	}

	/** staffProfileId scopes to one employee's history; status alone (no staffProfileId) is an
	 * org-wide inbox, e.g. "all pending requests awaiting a decision". At least one must be given. */
	@GetMapping
	public List<LeaveRequestResponse> list(
			@RequestParam(required = false) Long staffProfileId, @RequestParam(required = false) String status) {
		LeaveRequestStatus parsedStatus = status != null ? LeaveRequestStatus.valueOf(status) : null;
		List<LeaveRequest> requests;
		if (staffProfileId != null && parsedStatus != null) {
			requests = leaveRequestRepository.findByStaffProfileIdAndStatus(staffProfileId, parsedStatus);
		} else if (staffProfileId != null) {
			requests = leaveRequestRepository.findByStaffProfileId(staffProfileId);
		} else if (parsedStatus != null) {
			requests = leaveRequestRepository.findByStatus(parsedStatus);
		} else {
			throw new IllegalArgumentException("staffProfileId or status must be provided");
		}
		return requests.stream().map(LeaveRequestResponse::from).toList();
	}

	@PostMapping("/{id}/approve")
	@RequirePermission("HR_LEAVE_MANAGE")
	public LeaveRequestResponse approve(@PathVariable Long id, @RequestBody DecideLeaveRequestRequest request) {
		return LeaveRequestResponse.from(hrService.approve(findLeaveRequest(id), request.decidedBy()));
	}

	@PostMapping("/{id}/reject")
	@RequirePermission("HR_LEAVE_MANAGE")
	public LeaveRequestResponse reject(@PathVariable Long id, @RequestBody DecideLeaveRequestRequest request) {
		return LeaveRequestResponse.from(hrService.reject(findLeaveRequest(id), request.decidedBy()));
	}

	@PostMapping("/{id}/cancel")
	@RequirePermission("HR_LEAVE_MANAGE")
	public LeaveRequestResponse cancel(@PathVariable Long id) {
		return LeaveRequestResponse.from(hrService.cancel(findLeaveRequest(id)));
	}

	private LeaveRequest findLeaveRequest(Long id) {
		return leaveRequestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No leave request with id " + id));
	}

	private StaffProfile findStaffProfile(Long id) {
		return staffProfileRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + id));
	}

	private LeaveType findLeaveType(Long id) {
		return leaveTypeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No leave type with id " + id));
	}

	private AcademicYear findAcademicYear(Long id) {
		return academicYearRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No academic year with id " + id));
	}
}
