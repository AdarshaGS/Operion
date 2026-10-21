package com.operion.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.operion.academic.WorkingCalendarEntry;
import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.academic.WorkingCalendarEntryType;
import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import org.springframework.stereotype.Service;

/**
 * The single answer to "is this a working day?" - combines the organisation's recurring
 * weekly-off pattern (OrganisationConfiguration.workingDaysMask) with the dated exceptions
 * in the working calendar: a HOLIDAY turns a normal working day off, a SPECIAL_WORKING_DAY
 * turns a weekly-off day on. A campus-specific entry beats an org-wide one for the same
 * date; with no campus (org-wide staff, org-wide reminders) only org-wide entries apply.
 * HR leave and the schedulers that send things on the organisation's behalf go through
 * here instead of re-reading the mask and the calendar themselves.
 */
@Service
public class WorkingDayService {

	/** Monday-Saturday, same default OrganisationConfiguration starts with. */
	private static final int DEFAULT_MASK = 0b0111111;
	private static final int MAX_LOOKAHEAD_DAYS = 366;
	private static final String WEEKLY_OFF_LABEL = "Weekly off";

	private final WorkingCalendarEntryRepository entryRepository;
	private final OrganisationConfigurationRepository configurationRepository;

	public WorkingDayService(WorkingCalendarEntryRepository entryRepository,
			OrganisationConfigurationRepository configurationRepository) {
		this.entryRepository = entryRepository;
		this.configurationRepository = configurationRepository;
	}

	/** Every non-working day in [from, to], keyed (and sorted) by date. campusId may be null. */
	public Map<LocalDate, NonWorkingDay> nonWorkingDays(LocalDate from, LocalDate to, Long campusId) {
		Map<LocalDate, NonWorkingDay> result = new TreeMap<>();
		if (to.isBefore(from)) {
			return result;
		}
		int mask = configuration().map(OrganisationConfiguration::getWorkingDaysMask).orElse(DEFAULT_MASK);
		Map<LocalDate, WorkingCalendarEntry> entries = applicableEntries(from, to, campusId);
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			WorkingCalendarEntry entry = entries.get(date);
			if (entry != null) {
				if (entry.getType() == WorkingCalendarEntryType.HOLIDAY) {
					result.put(date, new NonWorkingDay(date, NonWorkingDay.Kind.HOLIDAY, entry.getLabel()));
				}
				continue; // SPECIAL_WORKING_DAY: working even if the weekly pattern says off
			}
			if ((mask & (1 << date.getDayOfWeek().ordinal())) == 0) { // bit 0 = Monday
				result.put(date, new NonWorkingDay(date, NonWorkingDay.Kind.WEEKLY_OFF, WEEKLY_OFF_LABEL));
			}
		}
		return result;
	}

	public boolean isWorkingDay(LocalDate date, Long campusId) {
		return !nonWorkingDays(date, date, campusId).containsKey(date);
	}

	/** The working days in [from, to], in date order. */
	public List<LocalDate> workingDays(LocalDate from, LocalDate to, Long campusId) {
		Map<LocalDate, NonWorkingDay> off = nonWorkingDays(from, to, campusId);
		List<LocalDate> days = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			if (!off.containsKey(date)) {
				days.add(date);
			}
		}
		return days;
	}

	public int countWorkingDays(LocalDate from, LocalDate to, Long campusId) {
		return workingDays(from, to, campusId).size();
	}

	/** The first working day on or after {@code from}; {@code from} itself if it already is one.
	 * Gives up after a year of consecutive off days (a misconfigured mask) and returns {@code from}. */
	public LocalDate nextWorkingDay(LocalDate from, Long campusId) {
		List<LocalDate> days = workingDays(from, from.plusDays(MAX_LOOKAHEAD_DAYS), campusId);
		return days.isEmpty() ? from : days.get(0);
	}

	/** Today's date in the organisation's own timezone (falls back to the server's). */
	public LocalDate today() {
		ZoneId zone = configuration().map(OrganisationConfiguration::getTimezone).map(WorkingDayService::zoneOrNull)
				.orElse(ZoneId.systemDefault());
		return LocalDate.now(zone);
	}

	private Map<LocalDate, WorkingCalendarEntry> applicableEntries(LocalDate from, LocalDate to, Long campusId) {
		Map<LocalDate, WorkingCalendarEntry> byDate = new HashMap<>();
		for (WorkingCalendarEntry entry : entryRepository.findByDateBetween(from, to)) {
			boolean orgWide = entry.getCampus() == null;
			boolean thisCampus = !orgWide && campusId != null && entry.getCampus().getId().equals(campusId);
			if (thisCampus || orgWide) {
				WorkingCalendarEntry existing = byDate.get(entry.getDate());
				if (existing == null || thisCampus) { // a campus-specific entry beats the org-wide one
					byDate.put(entry.getDate(), entry);
				}
			}
		}
		return byDate;
	}

	private java.util.Optional<OrganisationConfiguration> configuration() {
		return configurationRepository.findById(TenantContext.getOrganisationId());
	}

	private static ZoneId zoneOrNull(String id) {
		try {
			return id == null || id.isBlank() ? null : ZoneId.of(id);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
