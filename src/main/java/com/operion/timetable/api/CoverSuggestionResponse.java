package com.operion.timetable.api;

import java.time.LocalDate;

import com.operion.timetable.SubstitutionService.Suggestion;
import com.operion.timetable.TimetableEntry;

/** The cover the auto-suggester would propose for one uncovered slot; proposed is null, with reasonIfNone, when nobody is free. Nothing is saved. */
public record CoverSuggestionResponse(Long entryId, LocalDate date, String periodLabel, String sectionName, String subjectName,
		String absentTeacherName, SubstitutionNeedResponse.CandidateLine proposed, String reasonIfNone) {

	static CoverSuggestionResponse from(Suggestion suggestion) {
		TimetableEntry entry = suggestion.slot().need().entry();
		var absent = suggestion.slot().leave().getStaffProfile().getPerson();
		var proposed = suggestion.proposed();
		return new CoverSuggestionResponse(entry.getId(), suggestion.slot().need().date(), entry.getPeriod().getLabel(),
				entry.getSection().getName(), entry.getSubject().getName(), absent.getFirstName() + " " + absent.getLastName(),
				proposed == null ? null : SubstitutionNeedResponse.line(proposed), suggestion.reasonIfNone());
	}
}
