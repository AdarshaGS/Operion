package com.operion.hr.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.hr.HrService;
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.LeaveTypeStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr/leave-types")
@RequirePermission("HR_VIEW")
public class LeaveTypeController {

	private final HrService hrService;
	private final LeaveTypeRepository leaveTypeRepository;

	public LeaveTypeController(HrService hrService, LeaveTypeRepository leaveTypeRepository) {
		this.hrService = hrService;
		this.leaveTypeRepository = leaveTypeRepository;
	}

	@PostMapping
	@RequirePermission("HR_LEAVE_TYPE_MANAGE")
	public LeaveTypeResponse create(@Valid @RequestBody CreateLeaveTypeRequest request) {
		return LeaveTypeResponse.from(hrService.createLeaveType(request.code(), request.name(), request.defaultAnnualDays()));
	}

	@PutMapping("/{id}")
	@RequirePermission("HR_LEAVE_TYPE_MANAGE")
	public LeaveTypeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLeaveTypeRequest request) {
		LeaveType leaveType = leaveTypeRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No leave type with id " + id));
		return LeaveTypeResponse.from(hrService.updateLeaveType(leaveType, request.name(), request.defaultAnnualDays()));
	}

	@GetMapping
	public List<LeaveTypeResponse> list() {
		return leaveTypeRepository.findByStatus(LeaveTypeStatus.ACTIVE).stream().map(LeaveTypeResponse::from).toList();
	}
}
