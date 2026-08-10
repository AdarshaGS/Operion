package com.operion.hr.api;

import com.operion.hr.HrService;
import com.operion.hr.LeaveBalance;
import com.operion.hr.LeaveBalanceRepository;
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/leave-balances")
public class LeaveBalanceController {

	private final HrService hrService;
	private final LeaveBalanceRepository leaveBalanceRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final LeaveTypeRepository leaveTypeRepository;
	private final AcademicYearRepository academicYearRepository;

	public LeaveBalanceController(HrService hrService, LeaveBalanceRepository leaveBalanceRepository,
			StaffProfileRepository staffProfileRepository, LeaveTypeRepository leaveTypeRepository,
			AcademicYearRepository academicYearRepository) {
		this.hrService = hrService;
		this.leaveBalanceRepository = leaveBalanceRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.leaveTypeRepository = leaveTypeRepository;
		this.academicYearRepository = academicYearRepository;
	}

	@PostMapping
	public LeaveBalanceResponse allocate(@RequestBody AllocateLeaveBalanceRequest request) {
		StaffProfile staffProfile = findStaffProfile(request.staffProfileId());
		LeaveType leaveType = findLeaveType(request.leaveTypeId());
		AcademicYear academicYear = findAcademicYear(request.academicYearId());
		hrService.allocateBalance(staffProfile, leaveType, academicYear, request.allocatedDays());
		return toResponse(staffProfile, leaveType, academicYear);
	}

	@GetMapping
	public LeaveBalanceResponse get(@RequestParam Long staffProfileId, @RequestParam Long leaveTypeId, @RequestParam Long academicYearId) {
		return toResponse(findStaffProfile(staffProfileId), findLeaveType(leaveTypeId), findAcademicYear(academicYearId));
	}

	private LeaveBalanceResponse toResponse(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear) {
		double allocated = leaveBalanceRepository
				.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(staffProfile.getId(), leaveType.getId(), academicYear.getId())
				.map(LeaveBalance::getAllocatedDays)
				.orElse(0.0);
		double remaining = hrService.getRemainingBalance(staffProfile, leaveType, academicYear);
		return new LeaveBalanceResponse(staffProfile.getId(), leaveType.getId(), academicYear.getId(), allocated, remaining);
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
