package com.operion.timetable.api;

import java.time.DayOfWeek;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.timetable.Period;
import com.operion.timetable.PeriodRepository;
import com.operion.timetable.TimetableEntry;
import com.operion.timetable.TimetableEntryRepository;
import com.operion.timetable.TimetableService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePermission("TIMETABLE_VIEW")
public class TimetableController {

	private final TimetableService timetableService;
	private final PeriodRepository periodRepository;
	private final TimetableEntryRepository entryRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final PersonRepository personRepository;

	public TimetableController(TimetableService timetableService, PeriodRepository periodRepository,
			TimetableEntryRepository entryRepository, SectionRepository sectionRepository,
			SubjectRepository subjectRepository, PersonRepository personRepository) {
		this.timetableService = timetableService;
		this.periodRepository = periodRepository;
		this.entryRepository = entryRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.personRepository = personRepository;
	}

	@GetMapping("/api/v1/timetable/periods")
	public List<PeriodResponse> listPeriods() {
		return timetableService.listPeriods().stream().map(PeriodResponse::from).toList();
	}

	@PostMapping("/api/v1/timetable/periods")
	@RequirePermission("TIMETABLE_MANAGE")
	public PeriodResponse createPeriod(@Valid @RequestBody CreatePeriodRequest request) {
		return PeriodResponse.from(timetableService.createPeriod(request.ordinal(), request.label(),
				request.startTime(), request.endTime(), Boolean.TRUE.equals(request.breakSlot())));
	}

	@PutMapping("/api/v1/timetable/periods/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public PeriodResponse updatePeriod(@PathVariable Long id, @Valid @RequestBody PeriodRequest request) {
		return PeriodResponse.from(timetableService.updatePeriod(findPeriod(id), request.label(), request.startTime(),
				request.endTime(), Boolean.TRUE.equals(request.breakSlot())));
	}

	@DeleteMapping("/api/v1/timetable/periods/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public void deletePeriod(@PathVariable Long id) {
		timetableService.deletePeriod(findPeriod(id));
	}

	@GetMapping("/api/v1/sections/{sectionId}/timetable")
	public List<TimetableEntryResponse> forSection(@PathVariable Long sectionId) {
		if (!sectionRepository.existsById(sectionId)) {
			throw new IllegalArgumentException("No section with id " + sectionId);
		}
		return entryRepository.findBySectionId(sectionId).stream().map(TimetableEntryResponse::from).toList();
	}

	/** Optional academicYearId narrows to one year; without it every year's slots come back. */
	@GetMapping("/api/v1/teachers/{personId}/timetable")
	public List<TimetableEntryResponse> forTeacher(@PathVariable Long personId,
			@RequestParam(required = false) Long academicYearId) {
		return entryRepository.findByTeacherPersonId(personId).stream()
				.filter(entry -> academicYearId == null || entry.getAcademicYear().getId().equals(academicYearId))
				.map(TimetableEntryResponse::from)
				.toList();
	}

	@PutMapping("/api/v1/sections/{sectionId}/timetable")
	@RequirePermission("TIMETABLE_MANAGE")
	public TimetableEntryResponse setEntry(@PathVariable Long sectionId, @Valid @RequestBody SetTimetableEntryRequest request) {
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		Subject subject = subjectRepository.findById(request.subjectId())
				.orElseThrow(() -> new IllegalArgumentException("No subject with id " + request.subjectId()));
		Person teacher = personRepository.findById(request.teacherPersonId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.teacherPersonId()));
		DayOfWeek day = DayOfWeek.valueOf(request.dayOfWeek());
		return TimetableEntryResponse.from(timetableService.setEntry(section, subject, teacher, day, findPeriod(request.periodId())));
	}

	@DeleteMapping("/api/v1/timetable-entries/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public void removeEntry(@PathVariable Long id) {
		TimetableEntry entry = entryRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No timetable entry with id " + id));
		timetableService.removeEntry(entry);
	}

	private Period findPeriod(Long id) {
		return periodRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No period with id " + id));
	}
}
