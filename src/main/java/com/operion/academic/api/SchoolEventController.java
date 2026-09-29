package com.operion.academic.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.SchoolEvent;
import com.operion.academic.SchoolEventRepository;
import com.operion.authorization.RequirePermission;
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

/** No dedicated service - a school event has no business rules beyond "save the row",
 * same as WorkingCalendarController/CampusController. Hard-deletable: nothing else ever
 * references a SchoolEvent id. Part of #240's admin-side scope - surfacing this calendar
 * inside the Teacher/Student/Guardian portals is blocked on #207-209, which don't exist yet. */
@RestController
@RequestMapping("/api/v1/academic/school-events")
@RequirePermission("SCHOOL_EVENT_VIEW")
public class SchoolEventController {

	private final SchoolEventRepository schoolEventRepository;
	private final CampusRepository campusRepository;

	public SchoolEventController(SchoolEventRepository schoolEventRepository, CampusRepository campusRepository) {
		this.schoolEventRepository = schoolEventRepository;
		this.campusRepository = campusRepository;
	}

	@GetMapping
	public List<SchoolEventResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return schoolEventRepository.findByDateBetweenOrderByDateAsc(from, to).stream().map(SchoolEventResponse::from).toList();
	}

	@PostMapping
	@RequirePermission("SCHOOL_EVENT_MANAGE")
	public SchoolEventResponse create(@RequestBody CreateSchoolEventRequest request) {
		Campus campus = request.campusId() != null
				? campusRepository.findById(request.campusId())
						.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()))
				: null;
		SchoolEvent event = new SchoolEvent(campus, request.date(), request.title(), request.description());
		return SchoolEventResponse.from(schoolEventRepository.save(event));
	}

	@DeleteMapping("/{id}")
	@RequirePermission("SCHOOL_EVENT_MANAGE")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		schoolEventRepository.deleteById(id);
	}
}
