package com.operion.timetable.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.timetable.SubstitutionService;
import com.operion.timetable.TimetableEntry;
import com.operion.timetable.TimetableEntryRepository;
import com.operion.timetable.TimetableSubstitutionRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Substitute teachers: who is covering what on a date, what an absence leaves uncovered, and who could take it. */
@RestController
@RequirePermission("TIMETABLE_VIEW")
public class SubstitutionController {

	private final SubstitutionService substitutionService;
	private final TimetableEntryRepository entryRepository;
	private final TimetableSubstitutionRepository substitutionRepository;
	private final PersonRepository personRepository;

	public SubstitutionController(SubstitutionService substitutionService, TimetableEntryRepository entryRepository,
			TimetableSubstitutionRepository substitutionRepository, PersonRepository personRepository) {
		this.substitutionService = substitutionService;
		this.entryRepository = entryRepository;
		this.substitutionRepository = substitutionRepository;
		this.personRepository = personRepository;
	}

	@GetMapping("/api/v1/timetable/substitutions")
	public List<SubstitutionResponse> onDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return substitutionService.onDate(date).stream().map(SubstitutionResponse::from).toList();
	}

	/** The slots {@code teacherPersonId} would miss between from and to (at most 62 days), each with its cover or ranked candidates. */
	@GetMapping("/api/v1/timetable/substitutions/needs")
	public List<SubstitutionNeedResponse> needs(@RequestParam Long teacherPersonId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return substitutionService.needs(teacherPersonId, from, to == null ? from : to).stream().map(SubstitutionNeedResponse::from).toList();
	}

	@PostMapping("/api/v1/timetable/substitutions")
	@RequirePermission("TIMETABLE_MANAGE")
	public SubstitutionResponse assign(@Valid @RequestBody SubstitutionRequest request) {
		TimetableEntry entry = entryRepository.findById(request.entryId())
				.orElseThrow(() -> new IllegalArgumentException("No timetable entry with id " + request.entryId()));
		Person substitute = personRepository.findById(request.substituteTeacherPersonId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.substituteTeacherPersonId()));
		return SubstitutionResponse.from(substitutionService.assign(entry, request.date(), substitute, request.reason()));
	}

	@DeleteMapping("/api/v1/timetable/substitutions/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public void remove(@PathVariable Long id) {
		substitutionService.remove(substitutionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No substitution with id " + id)));
	}
}
