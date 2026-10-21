package com.operion.timetable;

import java.util.ArrayList;
import java.util.List;

import com.operion.academic.Section;
import com.operion.academic.YearStructureLookup;
import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.rollover.RolloverContributor;
import com.operion.organisation.rollover.RolloverOptions;
import com.operion.organisation.rollover.RolloverStep;
import com.operion.organisation.rollover.RolloverStep.Tally;
import org.springframework.stereotype.Component;

/** The weekly timetable, copied slot for slot onto the matching sections of the new year. A slot that would put a teacher in two places at once is left out and counted. */
@Component
public class TimetableRollover implements RolloverContributor {

	private final TimetableEntryRepository entryRepository;
	private final YearStructureLookup lookup;
	private final ClassPeriodTemplateRepository classTemplateRepository;

	TimetableRollover(TimetableEntryRepository entryRepository, YearStructureLookup lookup,
			ClassPeriodTemplateRepository classTemplateRepository) {
		this.entryRepository = entryRepository;
		this.classTemplateRepository = classTemplateRepository;
		this.lookup = lookup;
	}

	@Override
	public String key() {
		return "TIMETABLE";
	}

	@Override
	public String label() {
		return "Timetable";
	}

	@Override
	public AppModule module() {
		return AppModule.ACADEMICS;
	}

	@Override
	public int order() {
		return 30;
	}

	@Override
	public RolloverStep run(AcademicYear source, AcademicYear target, RolloverOptions options) {
		Tally days = new Tally("Class school days");
		for (ClassPeriodTemplate link : classTemplateRepository.findBySchoolClassAcademicYearId(source.getId())) {
			var targetClass = lookup.classIn(target, link.getSchoolClass()).orElse(null);
			if (targetClass == null) {
				continue;
			}
			if (classTemplateRepository.findBySchoolClassId(targetClass.getId()).isPresent()) {
				days.alreadyThere();
				continue;
			}
			classTemplateRepository.save(new ClassPeriodTemplate(targetClass, link.getTemplate()));
			days.copied();
		}
		Tally slots = new Tally("Timetable slots");
		int noSection = 0;
		int clashes = 0;
		for (TimetableEntry entry : entryRepository.findByAcademicYearId(source.getId())) {
			Section section = lookup.sectionIn(target, entry.getSection()).orElse(null);
			if (section == null) {
				noSection++;
				continue;
			}
			if (entryRepository.findBySectionIdAndDayOfWeekAndPeriodId(section.getId(), entry.getDayOfWeek(), entry.getPeriod().getId()).isPresent()) {
				slots.alreadyThere();
				continue;
			}
			if (entryRepository.findByAcademicYearIdAndTeacherPersonIdAndDayOfWeekAndPeriodId(
					target.getId(), entry.getTeacherPerson().getId(), entry.getDayOfWeek(), entry.getPeriod().getId()).isPresent()) {
				clashes++;
				continue;
			}
			entryRepository.save(new TimetableEntry(target, section, entry.getSubject(), entry.getTeacherPerson(), entry.getDayOfWeek(), entry.getPeriod(),
					roomFor(entry, target)));
			slots.copied();
		}
		List<String> notes = new ArrayList<>();
		if (noSection > 0) {
			notes.add(noSection + " slot(s) left out: their class or section does not exist in the new year yet (run the classes step first)");
		}
		if (clashes > 0) {
			notes.add(clashes + " slot(s) left out: the teacher is already teaching another section at that time");
		}
		return RolloverStep.of(this, List.of(days, slots), notes);
	}

	/** The slot keeps its room when that room is still active and nothing in the new year has taken it at that time; otherwise the slot is copied without one. */
	private Room roomFor(TimetableEntry entry, AcademicYear target) {
		Room room = entry.getRoom();
		if (room == null || !room.isActive()) {
			return null;
		}
		boolean taken = entryRepository.findByAcademicYearIdAndRoomId(target.getId(), room.getId()).stream()
				.anyMatch(other -> other.getDayOfWeek() == entry.getDayOfWeek() && other.getPeriod().getId().equals(entry.getPeriod().getId()));
		return taken ? null : room;
	}
}
