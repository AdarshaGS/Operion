package com.operion.timetable.api;

import java.time.DayOfWeek;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.timetable.GenerationResult;
import com.operion.timetable.Period;
import com.operion.timetable.PeriodRepository;
import com.operion.timetable.TeacherUnavailability;
import com.operion.timetable.TeacherUnavailabilityRepository;
import com.operion.timetable.TimetableGenerator;
import com.operion.timetable.TimetableService;
import com.operion.academic.SectionRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Teacher unavailability and the automatic timetable draft. */
@RestController
@RequirePermission("TIMETABLE_VIEW")
public class TimetableAvailabilityController {

	private final TimetableService timetableService;
	private final TimetableGenerator generator;
	private final TeacherUnavailabilityRepository unavailabilityRepository;
	private final PersonRepository personRepository;
	private final PeriodRepository periodRepository;
	private final SectionRepository sectionRepository;

	public TimetableAvailabilityController(TimetableService timetableService, TimetableGenerator generator,
			TeacherUnavailabilityRepository unavailabilityRepository, PersonRepository personRepository,
			PeriodRepository periodRepository, SectionRepository sectionRepository) {
		this.timetableService = timetableService;
		this.generator = generator;
		this.unavailabilityRepository = unavailabilityRepository;
		this.personRepository = personRepository;
		this.periodRepository = periodRepository;
		this.sectionRepository = sectionRepository;
	}

	@GetMapping("/api/v1/teachers/{personId}/unavailability")
	public List<UnavailabilityResponse> list(@PathVariable Long personId) {
		return timetableService.unavailabilityOf(personId).stream().map(UnavailabilityResponse::from).toList();
	}

	@PostMapping("/api/v1/teachers/{personId}/unavailability")
	@RequirePermission("TIMETABLE_MANAGE")
	public UnavailabilityResponse add(@PathVariable Long personId, @Valid @RequestBody UnavailabilityRequest request) {
		Person teacher = personRepository.findById(personId)
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + personId));
		Period period = request.periodId() == null ? null : periodRepository.findById(request.periodId())
				.orElseThrow(() -> new IllegalArgumentException("No period with id " + request.periodId()));
		return UnavailabilityResponse.from(timetableService.addUnavailability(teacher, DayOfWeek.valueOf(request.dayOfWeek()), period, request.reason()));
	}

	@DeleteMapping("/api/v1/timetable/unavailability/{id}")
	@RequirePermission("TIMETABLE_MANAGE")
	public void remove(@PathVariable Long id) {
		TeacherUnavailability unavailability = unavailabilityRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No unavailability record with id " + id));
		timetableService.removeUnavailability(unavailability);
	}

	@PostMapping("/api/v1/sections/{sectionId}/timetable/generate")
	@RequirePermission("TIMETABLE_MANAGE")
	public GenerationResult generate(@PathVariable Long sectionId, @RequestBody(required = false) GenerateTimetableRequest request) {
		var section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("No section with id " + sectionId));
		return generator.generateForSection(section, request != null && Boolean.TRUE.equals(request.clearExisting()));
	}

	@PostMapping("/api/v1/academic-years/{academicYearId}/timetable/generate")
	@RequirePermission("TIMETABLE_MANAGE")
	public GenerationResult generateYear(@PathVariable Long academicYearId, @RequestBody(required = false) GenerateTimetableRequest request) {
		return generator.generateForYear(academicYearId, request != null && Boolean.TRUE.equals(request.clearExisting()));
	}
}
