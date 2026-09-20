package com.operion.parent.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianImportService;
import com.operion.parent.GuardianRepository;
import com.operion.parent.ParentService;
import com.operion.parent.PortalInviteService;
import com.operion.parent.StudentGuardianRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/guardians")
@RequirePermission("GUARDIAN_VIEW")
public class GuardianController {

	private final ParentService parentService;
	private final GuardianRepository guardianRepository;
	private final PersonRepository personRepository;
	private final PortalInviteService portalInviteService;
	private final GuardianImportService guardianImportService;
	private final StudentGuardianRepository studentGuardianRepository;

	public GuardianController(ParentService parentService, GuardianRepository guardianRepository, PersonRepository personRepository,
			PortalInviteService portalInviteService, GuardianImportService guardianImportService,
			StudentGuardianRepository studentGuardianRepository) {
		this.parentService = parentService;
		this.guardianRepository = guardianRepository;
		this.personRepository = personRepository;
		this.portalInviteService = portalInviteService;
		this.guardianImportService = guardianImportService;
		this.studentGuardianRepository = studentGuardianRepository;
	}

	@PostMapping
	@RequirePermission("GUARDIAN_MANAGE")
	public GuardianResponse getOrCreate(@Valid @RequestBody CreateGuardianRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));

		return GuardianResponse.from(parentService.getOrCreateGuardian(person, request.occupation()));
	}

	@GetMapping("/{guardianId}")
	public GuardianResponse get(@PathVariable Long guardianId) {
		Guardian guardian = guardianRepository.findById(guardianId)
				.orElseThrow(() -> new IllegalArgumentException("No guardian with id " + guardianId));
		return GuardianResponse.from(guardian);
	}

	/**
	 * Returns the raw claim token once, here, and nowhere else - it is never stored or
	 * logged (only its bcrypt hash is). Staff hands the resulting link to the parent
	 * however they already do (WhatsApp, in person) - v1 has no automated email/SMS
	 * delivery, see the SaaS/RBAC-style "surface options rather than picking silently"
	 * decision this was built against.
	 */
	@PostMapping("/{guardianId}/grant-portal-access")
	@RequirePermission("GUARDIAN_MANAGE")
	public PortalInviteResponse grantPortalAccess(@PathVariable Long guardianId) {
		PortalInviteService.IssuedInvite invite = portalInviteService.issue(guardianId);
		return new PortalInviteResponse(invite.inviteId(), invite.rawToken(), invite.expiresAt());
	}

	/** Bulk CSV/Excel import, same per-row transaction isolation as StudentController's
	 * import endpoint - see GuardianImportService/GuardianRowImportService. */
	@PostMapping("/import")
	@RequirePermission("GUARDIAN_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean validateOnly) {
		return guardianImportService.importFile(file, validateOnly);
	}

	/** Inherits this controller's class-level GUARDIAN_VIEW gate, same convention as
	 * StudentController.export(). One row per Student<->Guardian link. */
	@GetMapping("/export")
	public List<GuardianExportResponse> export() {
		return studentGuardianRepository.findAll().stream().map(GuardianExportResponse::from).toList();
	}
}
