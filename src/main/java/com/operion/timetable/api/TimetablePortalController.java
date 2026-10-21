package com.operion.timetable.api;

import java.time.LocalDate;

import com.operion.common.TenantContext;
import com.operion.learning.ChildAccessService;
import com.operion.timetable.TimetablePortalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @RequirePermission}, same convention as the other /me portals: a teacher sees only
 * their own slots and a guardian only a linked child's section (ChildAccessService verifies the
 * StudentGuardian link). Unpublished sections are never shown.
 */
@RestController
@RequestMapping("/api/v1/me")
public class TimetablePortalController {

	private final TimetablePortalService portalService;
	private final ChildAccessService childAccess;

	public TimetablePortalController(TimetablePortalService portalService, ChildAccessService childAccess) {
		this.portalService = portalService;
		this.childAccess = childAccess;
	}

	/** {@code date} is any day in the week wanted; it defaults to today. */
	@GetMapping("/timetable")
	public PortalTimetableResponse mine(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return PortalTimetableResponse.from(portalService.forTeacher(TenantContext.getActorId(), date));
	}

	@GetMapping("/children/{studentId}/timetable")
	public PortalTimetableResponse child(@PathVariable Long studentId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		var child = childAccess.resolve(TenantContext.getActorId(), studentId);
		return PortalTimetableResponse.from(portalService.forSection(child.enrollment().getSection(), date));
	}
}
