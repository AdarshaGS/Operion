package com.operion.timetable.api;

import java.util.Comparator;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.timetable.Period;
import com.operion.timetable.PeriodTemplate;
import com.operion.timetable.TimetableService;
import com.operion.timetable.TimetableCsv;
import com.operion.timetable.TimetableEntry;
import com.operion.timetable.TimetableEntryRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Spreadsheet downloads of a section's week and of a teacher's week (every section they teach). */
@RestController
@RequirePermission("TIMETABLE_VIEW")
public class TimetableExportController {

	private final TimetableService timetableService;
	private final TimetableEntryRepository entryRepository;
	private final SectionRepository sectionRepository;
	private final PersonRepository personRepository;

	public TimetableExportController(TimetableService timetableService, TimetableEntryRepository entryRepository,
			SectionRepository sectionRepository, PersonRepository personRepository) {
		this.timetableService = timetableService;
		this.entryRepository = entryRepository;
		this.sectionRepository = sectionRepository;
		this.personRepository = personRepository;
	}

	@GetMapping(value = "/api/v1/sections/{sectionId}/timetable/export", produces = "text/csv")
	public ResponseEntity<String> section(@PathVariable Long sectionId) {
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		var schoolClass = section.getSchoolClass();
		String className = schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
		String csv = TimetableCsv.grid(timetableService.periodsFor(section), entryRepository.findBySectionId(sectionId),
				e -> e.getSubject().getName() + " (" + name(e.getTeacherPerson()) + ")" + room(e));
		return download(csv, "timetable-" + slug(className + "-" + section.getName()));
	}

	@GetMapping(value = "/api/v1/teachers/{personId}/timetable/export", produces = "text/csv")
	public ResponseEntity<String> teacher(@PathVariable Long personId) {
		Person teacher = personRepository.findById(personId)
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		List<TimetableEntry> entries = entryRepository.findByTeacherPersonId(personId).stream()
				.sorted(Comparator.comparing((TimetableEntry e) -> e.getAcademicYear().getStartDate()).reversed()).toList();
		// Only the latest academic year the teacher has slots in - older years would collide in one grid.
		if (!entries.isEmpty()) {
			Long latestYear = entries.get(0).getAcademicYear().getId();
			entries = entries.stream().filter(e -> e.getAcademicYear().getId().equals(latestYear)).toList();
		}
		String csv = TimetableCsv.grid(periodsOf(entries), entries, e -> {
			var schoolClass = e.getSection().getSchoolClass();
			String className = schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
			return e.getSubject().getName() + " (" + className + " " + e.getSection().getName() + ")" + room(e);
		});
		return download(csv, "timetable-" + slug(name(teacher)));
	}

	/** The periods of every school day the teacher's slots sit on, in clock order. */
	private List<Period> periodsOf(List<TimetableEntry> entries) {
		var days = new java.util.LinkedHashMap<Long, PeriodTemplate>();
		boolean defaultDay = entries.isEmpty();
		for (TimetableEntry entry : entries) {
			PeriodTemplate template = entry.getPeriod().getTemplate();
			if (template == null) {
				defaultDay = true;
			} else {
				days.putIfAbsent(template.getId(), template);
			}
		}
		var periods = new java.util.ArrayList<Period>(defaultDay ? timetableService.listPeriods() : List.of());
		days.values().forEach(template -> periods.addAll(timetableService.listPeriods(template)));
		periods.sort(Comparator.comparing(Period::getStartTime).thenComparing(Period::getOrdinal));
		return periods;
	}

	private static ResponseEntity<String> download(String csv, String fileName) {
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".csv\"")
				.contentType(MediaType.parseMediaType("text/csv")).body(csv);
	}

	private static String room(TimetableEntry entry) {
		return entry.getRoom() == null ? "" : " [" + entry.getRoom().getName() + "]";
	}

	private static String name(Person person) {
		return person.getFirstName() + " " + person.getLastName();
	}

	private static String slug(String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}
}
