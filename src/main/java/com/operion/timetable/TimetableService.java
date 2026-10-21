package com.operion.timetable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.ClassSubjectStatus;
import com.operion.academic.SchoolClass;
import com.operion.academic.Section;
import com.operion.academic.Subject;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimetableService {

	private final PeriodRepository periodRepository;
	private final TimetableEntryRepository entryRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final TimetableSettingsRepository settingsRepository;
	private final ClassSubjectRepository classSubjectRepository;
	private final RoomRepository roomRepository;
	private final TimetablePublicationRepository publicationRepository;
	private final TeacherUnavailabilityRepository unavailabilityRepository;
	private final TimetableSubstitutionRepository substitutionRepository;
	private final PeriodTemplateRepository templateRepository;
	private final ClassPeriodTemplateRepository classTemplateRepository;

	public TimetableService(PeriodRepository periodRepository, TimetableEntryRepository entryRepository,
			TeacherAssignmentRepository teacherAssignmentRepository, TimetableSettingsRepository settingsRepository,
			ClassSubjectRepository classSubjectRepository, RoomRepository roomRepository,
			TimetablePublicationRepository publicationRepository,
			TeacherUnavailabilityRepository unavailabilityRepository,
			TimetableSubstitutionRepository substitutionRepository, PeriodTemplateRepository templateRepository,
			ClassPeriodTemplateRepository classTemplateRepository) {
		this.periodRepository = periodRepository;
		this.entryRepository = entryRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.settingsRepository = settingsRepository;
		this.classSubjectRepository = classSubjectRepository;
		this.roomRepository = roomRepository;
		this.publicationRepository = publicationRepository;
		this.unavailabilityRepository = unavailabilityRepository;
		this.substitutionRepository = substitutionRepository;
		this.templateRepository = templateRepository;
		this.classTemplateRepository = classTemplateRepository;
	}

	public TimetableSettings settings() {
		return settingsRepository.findByOrganisationId(TenantContext.getOrganisationId()).orElseGet(TimetableSettings::new);
	}

	/** The default school day: the periods that belong to no template. */
	public List<Period> listPeriods() {
		return listPeriods(null);
	}

	public List<Period> listPeriods(PeriodTemplate template) {
		return template == null ? periodRepository.findByTemplateIsNullOrderByOrdinalAsc()
				: periodRepository.findByTemplateIdOrderByOrdinalAsc(template.getId());
	}

	/** The template a class follows; empty means the default day. */
	public Optional<PeriodTemplate> templateOf(SchoolClass schoolClass) {
		return classTemplateRepository.findBySchoolClassId(schoolClass.getId()).map(ClassPeriodTemplate::getTemplate);
	}

	/** The school day a section's class follows. */
	public List<Period> periodsFor(Section section) {
		return listPeriods(templateOf(section.getSchoolClass()).orElse(null));
	}

	public List<PeriodTemplate> listTemplates() {
		return templateRepository.findAllByOrderByNameAsc();
	}

	@Transactional
	public PeriodTemplate createTemplate(String name) {
		PeriodTemplate template = new PeriodTemplate(name);
		if (templateRepository.existsByNameIgnoreCase(template.getName())) {
			throw new IllegalStateException("A period template named " + template.getName() + " already exists");
		}
		return templateRepository.save(template);
	}

	@Transactional
	public PeriodTemplate renameTemplate(PeriodTemplate template, String name) {
		template.rename(name);
		if (templateRepository.existsByNameIgnoreCaseAndIdNot(template.getName(), template.getId())) {
			throw new IllegalStateException("A period template named " + template.getName() + " already exists");
		}
		return templateRepository.save(template);
	}

	/** A template in use by a class, or one that still has periods, is never deleted from under them. */
	@Transactional
	public void deleteTemplate(PeriodTemplate template) {
		if (classTemplateRepository.existsByTemplateId(template.getId())) {
			throw new IllegalStateException("Template " + template.getName() + " is used by a class - move the class to another day first");
		}
		if (periodRepository.existsByTemplateId(template.getId())) {
			throw new IllegalStateException("Template " + template.getName() + " still has periods - delete them first");
		}
		templateRepository.delete(template);
	}

	public long classesUsing(PeriodTemplate template) {
		return classTemplateRepository.countByTemplateId(template.getId());
	}

	/**
	 * Moves a class to a template (null for the default day). Refused once any of its sections has timetable
	 * entries: they sit on the old day's periods and would be stranded.
	 */
	@Transactional
	public void assignTemplate(SchoolClass schoolClass, PeriodTemplate template) {
		Long current = templateOf(schoolClass).map(PeriodTemplate::getId).orElse(null);
		Long wanted = template == null ? null : template.getId();
		if (java.util.Objects.equals(current, wanted)) {
			return;
		}
		if (entryRepository.existsBySectionSchoolClassId(schoolClass.getId())) {
			throw new IllegalStateException("This class already has timetable entries - clear its sections' timetables before changing its school day");
		}
		var existing = classTemplateRepository.findBySchoolClassId(schoolClass.getId());
		if (template == null) {
			existing.ifPresent(classTemplateRepository::delete);
		} else if (existing.isPresent()) {
			existing.get().useTemplate(template);
			classTemplateRepository.save(existing.get());
		} else {
			classTemplateRepository.save(new ClassPeriodTemplate(schoolClass, template));
		}
	}

	@Transactional
	public Period createPeriod(int ordinal, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		return createPeriod(ordinal, label, startTime, endTime, breakSlot ? SlotType.BREAK : SlotType.TEACHING);
	}

	@Transactional
	public Period createPeriod(int ordinal, String label, LocalTime startTime, LocalTime endTime, SlotType slotType) {
		return createPeriod(null, ordinal, label, startTime, endTime, slotType);
	}

	/** A period of the given school day (null is the default day); ordinals and times only have to be unique within that day. */
	@Transactional
	public Period createPeriod(PeriodTemplate template, int ordinal, String label, LocalTime startTime, LocalTime endTime, SlotType slotType) {
		boolean ordinalTaken = template == null ? periodRepository.existsByTemplateIsNullAndOrdinal(ordinal)
				: periodRepository.existsByTemplateIdAndOrdinal(template.getId(), ordinal);
		if (ordinalTaken) {
			throw new IllegalStateException("A period with ordinal " + ordinal + " already exists");
		}
		Period period = new Period(template, ordinal, label, startTime, endTime, slotType);
		boolean overlap = template == null ? periodRepository.existsByTemplateIsNullAndStartTimeLessThanAndEndTimeGreaterThan(endTime, startTime)
				: periodRepository.existsByTemplateIdAndStartTimeLessThanAndEndTimeGreaterThan(template.getId(), endTime, startTime);
		if (overlap) {
			throw new IllegalStateException("The period's times overlap an existing period");
		}
		requireMinimumGap(period, null);
		return periodRepository.save(period);
	}

	/** A slot that already holds entries can't stop being a teaching slot - it would strand them. */
	@Transactional
	public Period updatePeriod(Period period, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		return updatePeriod(period, label, startTime, endTime, breakSlot ? SlotType.BREAK : SlotType.TEACHING);
	}

	@Transactional
	public Period updatePeriod(Period period, String label, LocalTime startTime, LocalTime endTime, SlotType slotType) {
		if (!slotType.isTeaching() && entryRepository.existsByPeriodId(period.getId())) {
			throw new IllegalStateException("Period " + period.getLabel() + " has timetable entries and cannot become a "
					+ (slotType == SlotType.BREAK ? "break" : "special slot"));
		}
		boolean timingUnchanged = period.isTeaching() == slotType.isTeaching() && period.getStartTime().equals(startTime)
				&& period.getEndTime().equals(endTime);
		period.update(label, startTime, endTime, slotType);
		PeriodTemplate template = period.getTemplate();
		boolean overlap = template == null
				? periodRepository.existsByTemplateIsNullAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(period.getId(), endTime, startTime)
				: periodRepository.existsByTemplateIdAndIdNotAndStartTimeLessThanAndEndTimeGreaterThan(template.getId(), period.getId(), endTime, startTime);
		if (overlap) {
			throw new IllegalStateException("The period's times overlap an existing period");
		}
		if (!timingUnchanged) {
			requireMinimumGap(period, period.getId());
		}
		return periodRepository.save(period);
	}

	/**
	 * Two teaching periods that follow each other must be at least the configured gap apart. A break or special
	 * slot next door exempts that side, so "Period 2, Break, Period 3" can sit back to back whatever the gap is.
	 * Only the period being saved is judged - periods that already exist are never re-checked or moved.
	 */
	private void requireMinimumGap(Period candidate, Long ignoreId) {
		int gap = settings().getGapMinutes();
		if (gap <= 0 || !candidate.isTeaching()) {
			return;
		}
		List<Period> others = listPeriods(candidate.getTemplate()).stream()
				.filter(p -> ignoreId == null || !p.getId().equals(ignoreId)).toList();
		others.stream().filter(p -> !p.getEndTime().isAfter(candidate.getStartTime()))
				.max(java.util.Comparator.comparing(Period::getEndTime))
				.filter(Period::isTeaching)
				.ifPresent(before -> requireGap(before, candidate, gap));
		others.stream().filter(p -> !p.getStartTime().isBefore(candidate.getEndTime()))
				.min(java.util.Comparator.comparing(Period::getStartTime))
				.filter(Period::isTeaching)
				.ifPresent(after -> requireGap(candidate, after, gap));
	}

	private static void requireGap(Period earlier, Period later, int gap) {
		long actual = Duration.between(earlier.getEndTime(), later.getStartTime()).toMinutes();
		if (actual < gap) {
			throw new IllegalStateException(later.getLabel() + " must start at least " + gap + " minute" + (gap == 1 ? "" : "s")
					+ " after " + earlier.getLabel() + " ends (it is " + actual + ")");
		}
	}

	@Transactional
	public void deletePeriod(Period period) {
		if (entryRepository.existsByPeriodId(period.getId())) {
			throw new IllegalStateException("Period " + period.getLabel() + " has timetable entries - remove them first");
		}
		periodRepository.delete(period);
	}

	/**
	 * Places (or replaces) the entry for a section's (day, period) slot. Rejects break
	 * periods, a teacher who isn't currently assigned to teach the subject to the section,
	 * and a teacher already booked elsewhere in the same year/day/period. Replacing the
	 * slot's own existing entry is not a clash with itself.
	 */
	@Transactional
	public TimetableEntry setEntry(Section section, Subject subject, Person teacher, DayOfWeek day, Period period) {
		return setEntry(section, subject, teacher, day, period, null);
	}

	/** As above, optionally holding the slot in a room: it must be active and not used by another section in an overlapping period that day. */
	@Transactional
	public TimetableEntry setEntry(Section section, Subject subject, Person teacher, DayOfWeek day, Period period, Room room) {
		if (!period.isTeaching()) {
			throw new IllegalStateException("Cannot schedule a subject in a " + (period.getSlotType() == SlotType.BREAK ? "break" : "special")
					+ " period (" + period.getLabel() + ")");
		}
		Long classTemplate = templateOf(section.getSchoolClass()).map(PeriodTemplate::getId).orElse(null);
		Long periodTemplate = period.getTemplate() == null ? null : period.getTemplate().getId();
		if (!java.util.Objects.equals(classTemplate, periodTemplate)) {
			throw new IllegalStateException(period.getLabel() + " is not part of the school day this class follows");
		}
		if (!teacherAssignmentRepository.existsBySectionIdAndSubjectIdAndTeacherPersonIdAndStatus(
				section.getId(), subject.getId(), teacher.getId(), TeacherAssignmentStatus.ACTIVE)) {
			throw new IllegalStateException("Teacher is not currently assigned to teach " + subject.getName() + " to this section");
		}

		AcademicYear academicYear = section.getSchoolClass().getAcademicYear();
		Optional<TimetableEntry> existing =
				entryRepository.findBySectionIdAndDayOfWeekAndPeriodId(section.getId(), day, period.getId());

		unavailabilityRepository.findByTeacherPersonId(teacher.getId()).stream()
				.filter(block -> block.blocks(day, period)).findFirst()
				.ifPresent(block -> {
					throw new IllegalStateException(teacher.getFirstName() + " " + teacher.getLastName() + " is marked unavailable on "
							+ day + (block.getPeriod() == null ? "" : " " + block.getPeriod().getLabel())
							+ (block.getReason() == null ? "" : " (" + block.getReason() + ")"));
				});
		List<TimetableEntry> teacherSlots = entryRepository
				.findByAcademicYearIdAndTeacherPersonId(academicYear.getId(), teacher.getId()).stream()
				.filter(slot -> existing.isEmpty() || !slot.getId().equals(existing.get().getId())).toList();
		teacherSlots.stream()
				.filter(slot -> slot.getDayOfWeek() == day && overlaps(slot.getPeriod(), period))
				.findFirst()
				.ifPresent(clash -> {
					throw new IllegalStateException("Teacher is already booked on " + day + " " + clash.getPeriod().getLabel()
							+ " (" + className(clash.getSection()) + " " + clash.getSection().getName() + ")");
				});
		requireWithinWorkload(teacher, day, teacherSlots);
		requireWithinSubjectQuota(section, subject, existing.orElse(null));
		if (room != null) {
			requireRoomFree(room, academicYear, day, period, existing.orElse(null));
		}

		if (existing.isPresent()) {
			if (!existing.get().getTeacherPerson().getId().equals(teacher.getId())) {
				substitutionRepository.deleteByEntryId(existing.get().getId()); // they covered the previous teacher's absence
			}
			existing.get().reassign(subject, teacher, room);
			return entryRepository.save(existing.get());
		}
		return entryRepository.save(new TimetableEntry(academicYear, section, subject, teacher, day, period, room));
	}

	private void requireRoomFree(Room room, AcademicYear academicYear, DayOfWeek day, Period period, TimetableEntry replacing) {
		if (!room.isActive()) {
			throw new IllegalStateException("Room " + room.getName() + " is inactive and cannot be used for new slots");
		}
		entryRepository.findByAcademicYearIdAndRoomId(academicYear.getId(), room.getId()).stream()
				.filter(slot -> replacing == null || !slot.getId().equals(replacing.getId()))
				.filter(slot -> slot.getDayOfWeek() == day && overlaps(slot.getPeriod(), period))
				.findFirst()
				.ifPresent(clash -> {
					throw new IllegalStateException("Room " + room.getName() + " is already booked on " + day + " "
							+ clash.getPeriod().getLabel() + " (" + className(clash.getSection()) + " " + clash.getSection().getName() + ")");
				});
	}

	@Transactional
	public Room createRoom(String name, String roomType, Integer capacity) {
		if (roomRepository.existsByNameIgnoreCase(name == null ? "" : name.trim())) {
			throw new IllegalStateException("A room named " + name.trim() + " already exists");
		}
		return roomRepository.save(new Room(name, roomType, capacity));
	}

	@Transactional
	public Room updateRoom(Room room, String name, String roomType, Integer capacity, boolean active) {
		if (name != null && roomRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), room.getId())) {
			throw new IllegalStateException("A room named " + name.trim() + " already exists");
		}
		room.update(name, roomType, capacity, active);
		return roomRepository.save(room);
	}

	@Transactional
	public void deleteRoom(Room room) {
		if (entryRepository.existsByRoomId(room.getId())) {
			throw new IllegalStateException("Room " + room.getName() + " is used by timetable slots - mark it inactive instead");
		}
		roomRepository.delete(room);
	}

	/** Publishes the section's timetable (idempotent). An empty timetable has nothing to show, so it cannot be published. */
	@Transactional
	public TimetablePublication publish(Section section) {
		return publicationRepository.findBySectionId(section.getId()).orElseGet(() -> {
			if (entryRepository.findBySectionId(section.getId()).isEmpty()) {
				throw new IllegalStateException("The timetable has no slots yet - there is nothing to publish");
			}
			return publicationRepository.save(new TimetablePublication(section));
		});
	}

	@Transactional
	public void unpublish(Section section) {
		publicationRepository.findBySectionId(section.getId()).ifPresent(publicationRepository::delete);
	}

	public Optional<TimetablePublication> publication(Long sectionId) {
		return publicationRepository.findBySectionId(sectionId);
	}

	public boolean isPublished(Long sectionId) {
		return publicationRepository.findBySectionId(sectionId).isPresent();
	}

	/** Publishes every section of the year that has slots; returns how many were newly published. */
	@Transactional
	public int publishYear(Long academicYearId) {
		int newlyPublished = 0;
		java.util.Map<Long, Section> sections = new java.util.LinkedHashMap<>();
		entryRepository.findByAcademicYearId(academicYearId).forEach(entry -> sections.putIfAbsent(entry.getSection().getId(), entry.getSection()));
		for (Section section : sections.values()) {
			if (!isPublished(section.getId())) {
				publish(section);
				newlyPublished++;
			}
		}
		return newlyPublished;
	}

	@Transactional
	public TeacherUnavailability addUnavailability(Person teacher, DayOfWeek day, Period periodOrNull, String reason) {
		boolean duplicate = unavailabilityRepository.findByTeacherPersonId(teacher.getId()).stream()
				.anyMatch(u -> u.getDayOfWeek() == day && (u.getPeriod() == null
						? periodOrNull == null : periodOrNull != null && u.getPeriod().getId().equals(periodOrNull.getId())));
		if (duplicate) {
			throw new IllegalStateException("That unavailability is already recorded for this teacher");
		}
		if (periodOrNull != null && !periodOrNull.isTeaching()) {
			throw new IllegalStateException("Only teaching periods can be marked unavailable (" + periodOrNull.getLabel() + " is not one)");
		}
		return unavailabilityRepository.save(new TeacherUnavailability(teacher, day, periodOrNull, reason));
	}

	@Transactional
	public void removeUnavailability(TeacherUnavailability unavailability) {
		unavailabilityRepository.delete(unavailability);
	}

	public List<TeacherUnavailability> unavailabilityOf(Long teacherPersonId) {
		return unavailabilityRepository.findByTeacherPersonId(teacherPersonId);
	}

	/** Removes every slot of the section's timetable (used before regenerating). A published timetable must be unpublished first. */
	@Transactional
	public int clearSection(Section section) {
		if (isPublished(section.getId())) {
			throw new IllegalStateException("The timetable is published - unpublish it before clearing or regenerating it");
		}
		List<TimetableEntry> entries = entryRepository.findBySectionId(section.getId());
		entries.forEach(entry -> substitutionRepository.deleteByEntryId(entry.getId()));
		entryRepository.deleteAll(entries);
		return entries.size();
	}

	public List<Room> listRooms() {
		return roomRepository.findAllByOrderByNameAsc();
	}

	/** A subject with a weekly quota in the class can't be placed more often than that; swapping a slot's own subject isn't an extra one. */
	private void requireWithinSubjectQuota(Section section, Subject subject, TimetableEntry replacing) {
		Integer quota = quotaFor(section, subject);
		if (quota == null || (replacing != null && replacing.getSubject().getId().equals(subject.getId()))) {
			return;
		}
		long placed = entryRepository.findBySectionId(section.getId()).stream()
				.filter(entry -> entry.getSubject().getId().equals(subject.getId())).count();
		if (placed + 1 > quota) {
			throw new IllegalStateException(subject.getName() + " already has its " + quota + " weekly period" + (quota == 1 ? "" : "s")
					+ " in this section");
		}
	}

	private Integer quotaFor(Section section, Subject subject) {
		return classSubjectRepository.findBySchoolClassId(section.getSchoolClass().getId()).stream()
				.filter(cs -> cs.getStatus() == ClassSubjectStatus.ACTIVE && cs.getSubject().getId().equals(subject.getId()))
				.map(ClassSubject::getWeeklyPeriods).filter(java.util.Objects::nonNull).findFirst().orElse(null);
	}

	/** Per-subject quota against what is placed: every subject with a quota, plus any subject that has slots without one. */
	public List<SubjectCoverage> coverage(Section section) {
		List<TimetableEntry> entries = entryRepository.findBySectionId(section.getId());
		java.util.Map<Long, SubjectCoverage> byId = new java.util.LinkedHashMap<>();
		for (ClassSubject cs : classSubjectRepository.findBySchoolClassId(section.getSchoolClass().getId())) {
			if (cs.getStatus() == ClassSubjectStatus.ACTIVE && cs.getWeeklyPeriods() != null) {
				byId.put(cs.getSubject().getId(), new SubjectCoverage(cs.getSubject().getId(), cs.getSubject().getName(),
						cs.getWeeklyPeriods(), 0));
			}
		}
		for (TimetableEntry entry : entries) {
			Subject subject = entry.getSubject();
			SubjectCoverage current = byId.getOrDefault(subject.getId(), new SubjectCoverage(subject.getId(), subject.getName(), null, 0));
			byId.put(subject.getId(), new SubjectCoverage(current.subjectId(), current.subjectName(), current.requiredPerWeek(), current.placed() + 1));
		}
		return List.copyOf(byId.values());
	}

	private static boolean overlaps(Period a, Period b) {
		return a.getStartTime().isBefore(b.getEndTime()) && a.getEndTime().isAfter(b.getStartTime());
	}

	/** otherSlots is the teacher's year without the slot being written, so a replacement never counts twice. */
	private void requireWithinWorkload(Person teacher, DayOfWeek day, List<TimetableEntry> otherSlots) {
		TimetableSettings settings = settings();
		String who = teacher.getFirstName() + " " + teacher.getLastName();
		Integer perDay = settings.getMaxTeacherPeriodsPerDay();
		if (perDay != null && otherSlots.stream().filter(slot -> slot.getDayOfWeek() == day).count() + 1 > perDay) {
			throw new IllegalStateException(who + " would exceed the limit of " + perDay + " period" + (perDay == 1 ? "" : "s")
					+ " a day on " + day);
		}
		Integer perWeek = settings.getMaxTeacherPeriodsPerWeek();
		if (perWeek != null && otherSlots.size() + 1 > perWeek) {
			throw new IllegalStateException(who + " would exceed the limit of " + perWeek + " period" + (perWeek == 1 ? "" : "s")
					+ " a week");
		}
	}

	@Transactional
	public void removeEntry(TimetableEntry entry) {
		substitutionRepository.deleteByEntryId(entry.getId());
		entryRepository.delete(entry);
	}

	private static String className(Section section) {
		String displayName = section.getSchoolClass().getDisplayName();
		return displayName != null ? displayName : section.getSchoolClass().getGradeLevel().getName();
	}
}
