package com.operion.calendar.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.operion.calendar.NonWorkingDay;
import com.operion.calendar.WorkingDayService;
import com.operion.common.InvalidRequestException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Deliberately not permission-gated, like the other cross-cutting lookups (see
 * RequirePermission's class doc): a staff member in the portal needs to see which days
 * count as leave without holding WORKING_CALENDAR_VIEW. It exposes only dates and a
 * holiday name, nothing about people. */
@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

	private static final long MAX_RANGE_DAYS = 400;

	private final WorkingDayService workingDayService;

	public CalendarController(WorkingDayService workingDayService) {
		this.workingDayService = workingDayService;
	}

	@GetMapping("/days")
	public CalendarDaysResponse days(@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(required = false) Long campusId) {
		if (to.isBefore(from)) {
			throw new InvalidRequestException("'to' cannot be before 'from'");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new InvalidRequestException("Date range is limited to " + MAX_RANGE_DAYS + " days");
		}
		List<NonWorkingDay> off = List.copyOf(workingDayService.nonWorkingDays(from, to, campusId).values());
		long calendarDays = ChronoUnit.DAYS.between(from, to) + 1;
		return CalendarDaysResponse.of(from, to, (int) (calendarDays - off.size()), off);
	}
}
