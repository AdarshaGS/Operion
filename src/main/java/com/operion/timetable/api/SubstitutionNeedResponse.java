package com.operion.timetable.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.operion.timetable.SubstitutionService.Candidate;
import com.operion.timetable.SubstitutionService.Need;
import com.operion.timetable.TimetableEntry;

/** A slot an absent teacher misses on a date; substitution is set when it is already covered, otherwise candidates lists who could take it. */
public record SubstitutionNeedResponse(LocalDate date, String dayOfWeek, Long entryId, Long sectionId, String sectionName, String className,
		String subjectName, Long periodId, String periodLabel, LocalTime startTime, LocalTime endTime, SubstitutionResponse substitution,
		List<CandidateLine> candidates) {

	public record CandidateLine(Long personId, String name, String rank) {
	}

	static SubstitutionNeedResponse from(Need need) {
		TimetableEntry entry = need.entry();
		var schoolClass = entry.getSection().getSchoolClass();
		String className = schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
		return new SubstitutionNeedResponse(need.date(), need.date().getDayOfWeek().name(), entry.getId(), entry.getSection().getId(),
				entry.getSection().getName(), className, entry.getSubject().getName(), entry.getPeriod().getId(), entry.getPeriod().getLabel(),
				entry.getPeriod().getStartTime(), entry.getPeriod().getEndTime(),
				need.substitution() == null ? null : SubstitutionResponse.from(need.substitution()),
				need.candidates().stream().map(SubstitutionNeedResponse::line).toList());
	}

	private static CandidateLine line(Candidate c) {
		return new CandidateLine(c.person().getId(), c.person().getFirstName() + " " + c.person().getLastName(), c.rank());
	}
}
