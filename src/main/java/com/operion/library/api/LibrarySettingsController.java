package com.operion.library.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.library.LibrarySettings;
import com.operion.library.LibrarySettingsRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET is open to any authenticated org member and returns in-memory defaults rather
 * than 404ing when the org hasn't configured this yet, same convention as
 * ExaminationSettingsController. PUT upserts, since the row is created lazily on first
 * save. Per #169/#170. */
@RestController
@RequestMapping("/api/v1/library/settings")
public class LibrarySettingsController {

	private final LibrarySettingsRepository librarySettingsRepository;

	public LibrarySettingsController(LibrarySettingsRepository librarySettingsRepository) {
		this.librarySettingsRepository = librarySettingsRepository;
	}

	@GetMapping
	public LibrarySettingsResponse get() {
		return librarySettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.map(LibrarySettingsResponse::from)
				.orElseGet(LibrarySettingsResponse::defaults);
	}

	@PutMapping
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public LibrarySettingsResponse update(@RequestBody UpdateLibrarySettingsRequest request) {
		LibrarySettings settings = librarySettingsRepository.findByOrganisationId(TenantContext.getOrganisationId())
				.orElseGet(LibrarySettings::new);
		settings.setMaxLoansStudent(request.maxLoansStudent());
		settings.setMaxLoansStaff(request.maxLoansStaff());
		settings.setBorrowingPeriodDaysStudent(request.borrowingPeriodDaysStudent());
		settings.setBorrowingPeriodDaysStaff(request.borrowingPeriodDaysStaff());
		settings.setBlockIssueOnOverdue(request.blockIssueOnOverdue());
		return LibrarySettingsResponse.from(librarySettingsRepository.save(settings));
	}
}
