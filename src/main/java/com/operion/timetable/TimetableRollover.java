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

	TimetableRollover(TimetableEntryRepository entryRepository, YearStructureLookup lookup) {
		this.entryRepository = entryRepository;
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
			entryRepository.save(new TimetableEntry(target, section, entry.getSubject(), entry.getTeacherPerson(), entry.getDayOfWeek(), entry.getPeriod()));
			slots.copied();
		}
		List<String> notes = new ArrayList<>();
		if (noSection > 0) {
			notes.add(noSection + " slot(s) left out: their class or section does not exist in the new year yet (run the classes step first)");
		}
		if (clashes > 0) {
			notes.add(clashes + " slot(s) left out: the teacher is already teaching another section at that time");
		}
		return RolloverStep.of(this, List.of(slots), notes);
	}
}
