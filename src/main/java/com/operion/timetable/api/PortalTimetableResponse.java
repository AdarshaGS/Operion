package com.operion.timetable.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.timetable.TimetablePortalService;

/** published is false for a guardian whose child's section is still a draft - periods come back, entries do not. */
public record PortalTimetableResponse(LocalDate weekStart, LocalDate weekEnd, boolean published, List<PeriodResponse> periods,
		List<TimetableEntryResponse> entries, List<Cover> covers) {

	/** role: COVERING, COVERED or SUBSTITUTED - see TimetablePortalService.Cover. */
	public record Cover(String role, SubstitutionResponse substitution) {
	}

	static PortalTimetableResponse from(TimetablePortalService.View view) {
		return new PortalTimetableResponse(view.weekStart(), view.weekEnd(), view.published(),
				view.periods().stream().map(PeriodResponse::from).toList(),
				view.entries().stream().map(TimetableEntryResponse::from).toList(),
				view.covers().stream().map(c -> new Cover(c.role(), SubstitutionResponse.from(c.substitution()))).toList());
	}
}
