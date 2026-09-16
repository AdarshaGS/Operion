package com.operion.academic.api;

import java.util.List;

import com.operion.academic.WorkingCalendarEntry;
import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.academic.WorkingCalendarEntryType;
import com.operion.authorization.RequirePermission;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated service - a working calendar entry has no business rules beyond "save the
 * row", same as CampusController. Entries are hard-deletable (unlike most entities in this
 * codebase, which use a status field instead): nothing else ever references a
 * WorkingCalendarEntry id, so there's no history to preserve by keeping a cancelled row. */
@RestController
@RequestMapping("/api/v1/academic/working-calendar")
@RequirePermission("WORKING_CALENDAR_VIEW")
public class WorkingCalendarController {

	private final WorkingCalendarEntryRepository entryRepository;
	private final AcademicYearRepository academicYearRepository;
	private final CampusRepository campusRepository;

	public WorkingCalendarController(WorkingCalendarEntryRepository entryRepository,
			AcademicYearRepository academicYearRepository, CampusRepository campusRepository) {
		this.entryRepository = entryRepository;
		this.academicYearRepository = academicYearRepository;
		this.campusRepository = campusRepository;
	}

	@GetMapping
	public List<WorkingCalendarEntryResponse> list(@RequestParam Long academicYearId) {
		return entryRepository.findByAcademicYearIdOrderByDateAsc(academicYearId).stream()
				.map(WorkingCalendarEntryResponse::from).toList();
	}

	@PostMapping
	@RequirePermission("WORKING_CALENDAR_MANAGE")
	public WorkingCalendarEntryResponse create(@RequestBody CreateWorkingCalendarEntryRequest request) {
		AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + request.academicYearId()));
		Campus campus = request.campusId() != null
				? campusRepository.findById(request.campusId())
						.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()))
				: null;
		WorkingCalendarEntry entry = new WorkingCalendarEntry(academicYear, campus, request.date(), request.label(),
				WorkingCalendarEntryType.valueOf(request.type()));
		return WorkingCalendarEntryResponse.from(entryRepository.save(entry));
	}

	@DeleteMapping("/{id}")
	@RequirePermission("WORKING_CALENDAR_MANAGE")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		entryRepository.deleteById(id);
	}
}
