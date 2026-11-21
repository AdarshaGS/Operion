package com.operion.timetable.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.operion.hr.LeaveRequest;
import com.operion.identity.Person;
import com.operion.timetable.SubstitutionService.LeaveSlot;

/**
 * A slot an approved leave leaves without its teacher: everything a {@link SubstitutionNeedResponse} carries, plus the leave
 * behind it, the absent teacher and the campus of the slot's section (null when the section has none).
 */
public record UncoveredSlotResponse(LocalDate date, String dayOfWeek, Long entryId, Long sectionId, String sectionName, String className,
		String subjectName, Long periodId, String periodLabel, LocalTime startTime, LocalTime endTime, SubstitutionResponse substitution,
		List<SubstitutionNeedResponse.CandidateLine> candidates, Long leaveRequestId, Long absentTeacherPersonId, String absentTeacherName,
		Long campusId) {

	static UncoveredSlotResponse from(LeaveSlot slot) {
		SubstitutionNeedResponse need = SubstitutionNeedResponse.from(slot.need());
		LeaveRequest leave = slot.leave();
		Person absent = leave.getStaffProfile().getPerson();
		return new UncoveredSlotResponse(need.date(), need.dayOfWeek(), need.entryId(), need.sectionId(), need.sectionName(), need.className(),
				need.subjectName(), need.periodId(), need.periodLabel(), need.startTime(), need.endTime(), need.substitution(), need.candidates(),
				leave.getId(), absent.getId(), absent.getFirstName() + " " + absent.getLastName(), slot.campusId());
	}
}
