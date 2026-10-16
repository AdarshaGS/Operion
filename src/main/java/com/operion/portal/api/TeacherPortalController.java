package com.operion.portal.api;

import java.util.List;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.RequireModule;
import com.operion.hr.api.LeaveTypeResponse;
import com.operion.portal.TeacherLeaveBalanceSummary;
import com.operion.portal.TeacherLeaveRequestSummary;
import com.operion.portal.TeacherPortalService;
import com.operion.portal.TeacherSectionSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @RequirePermission} - same convention as MeController/GuardianPortalController.
 * Every method is scoped to the caller's own StaffProfile/TeacherAssignments, see
 * TeacherPortalService.
 */
@RestController
@RequestMapping("/api/v1/me")
public class TeacherPortalController {

	private final TeacherPortalService teacherPortalService;

	public TeacherPortalController(TeacherPortalService teacherPortalService) {
		this.teacherPortalService = teacherPortalService;
	}

	@GetMapping("/sections")
	@RequireModule(AppModule.ACADEMICS)
	public List<TeacherSectionSummary> sections() {
		return teacherPortalService.sectionsFor(TenantContext.getActorId());
	}

	@GetMapping("/leave-types")
	@RequireModule(AppModule.HR)
	public List<LeaveTypeResponse> leaveTypes() {
		return teacherPortalService.leaveTypeCatalog().stream().map(LeaveTypeResponse::from).toList();
	}

	@GetMapping("/leave-balance")
	@RequireModule(AppModule.HR)
	public List<TeacherLeaveBalanceSummary> leaveBalance(@RequestParam Long academicYearId) {
		return teacherPortalService.leaveBalanceFor(TenantContext.getActorId(), academicYearId);
	}

	@GetMapping("/leave-requests")
	@RequireModule(AppModule.HR)
	public List<TeacherLeaveRequestSummary> leaveRequests() {
		return teacherPortalService.leaveRequestsFor(TenantContext.getActorId());
	}

	@PostMapping("/leave-requests")
	@RequireModule(AppModule.HR)
	public TeacherLeaveRequestSummary raiseLeaveRequest(@RequestBody RaiseOwnLeaveRequestRequest request) {
		return teacherPortalService.raiseLeaveRequest(TenantContext.getActorId(), request.leaveTypeId(), request.academicYearId(),
				request.startDate(), request.endDate(), request.numberOfDays(), request.reason());
	}
}
