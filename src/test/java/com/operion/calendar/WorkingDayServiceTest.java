package com.operion.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.academic.WorkingCalendarEntry;
import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.academic.WorkingCalendarEntryType;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the one "is this a working day" rule: the weekly-off mask, dated HOLIDAYs that turn a
 * working day off, SPECIAL_WORKING_DAYs that turn a weekly-off day on, and campus-specific
 * entries beating org-wide ones. Dates used: 2026-01-05 is a Monday, 2026-01-11 a Sunday.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, WorkingDayService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkingDayServiceTest {

	private static final int MON_TO_FRI = 0b0011111;

	@Autowired
	private WorkingDayService workingDayService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private OrganisationConfigurationRepository configurationRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private WorkingCalendarEntryRepository entryRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(AcademicYear year, Campus campusA, Campus campusB) {
	}

	private Fixture setUp(String slug, Integer mask) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		if (mask != null) {
			OrganisationConfiguration configuration = new OrganisationConfiguration(organisation.getId(), "UTC");
			configuration.setWorkingDaysMask(mask);
			configurationRepository.save(configuration);
		}
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		return new Fixture(year, campusRepository.save(new Campus("Campus A", "A")), campusRepository.save(new Campus("Campus B", "B")));
	}

	private void entry(Fixture f, Campus campus, LocalDate date, String label, WorkingCalendarEntryType type) {
		entryRepository.save(new WorkingCalendarEntry(f.year(), campus, date, label, type));
	}

	@Test
	void withNoConfigurationRowSundayIsTheWeeklyOff() {
		setUp("wd-default", null);

		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 5), null)).isTrue();
		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 10), null)).isTrue(); // Saturday
		var off = workingDayService.nonWorkingDays(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), null);
		assertThat(off).containsOnlyKeys(LocalDate.of(2026, 1, 11));
		assertThat(off.get(LocalDate.of(2026, 1, 11)).kind()).isEqualTo(NonWorkingDay.Kind.WEEKLY_OFF);
	}

	@Test
	void aHolidayTurnsAWorkingDayOffAndCarriesItsName() {
		Fixture f = setUp("wd-holiday", null);
		entry(f, null, LocalDate.of(2026, 1, 6), "Pongal", WorkingCalendarEntryType.HOLIDAY);

		var off = workingDayService.nonWorkingDays(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), null);

		assertThat(off).containsOnlyKeys(LocalDate.of(2026, 1, 6));
		assertThat(off.get(LocalDate.of(2026, 1, 6)).kind()).isEqualTo(NonWorkingDay.Kind.HOLIDAY);
		assertThat(off.get(LocalDate.of(2026, 1, 6)).label()).isEqualTo("Pongal");
	}

	@Test
	void aSpecialWorkingDayTurnsAWeeklyOffDayOn() {
		Fixture f = setUp("wd-special", null);
		entry(f, null, LocalDate.of(2026, 1, 11), "Makeup day", WorkingCalendarEntryType.SPECIAL_WORKING_DAY);

		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 11), null)).isTrue();
	}

	@Test
	void aCampusEntryBeatsTheOrgWideOneForThatCampusOnly() {
		Fixture f = setUp("wd-campus-precedence", null);
		entry(f, null, LocalDate.of(2026, 1, 7), "Founders day", WorkingCalendarEntryType.HOLIDAY);
		entry(f, f.campusA(), LocalDate.of(2026, 1, 7), "Campus A is open", WorkingCalendarEntryType.SPECIAL_WORKING_DAY);

		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 7), f.campusA().getId())).isTrue();
		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 7), f.campusB().getId())).isFalse();
		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 7), null)).isFalse();
	}

	@Test
	void aCampusHolidayDoesNotApplyToOtherCampusesOrOrgWideLookups() {
		Fixture f = setUp("wd-campus-holiday", null);
		entry(f, f.campusA(), LocalDate.of(2026, 1, 8), "Campus A closed", WorkingCalendarEntryType.HOLIDAY);

		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 8), f.campusA().getId())).isFalse();
		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 8), f.campusB().getId())).isTrue();
		assertThat(workingDayService.isWorkingDay(LocalDate.of(2026, 1, 8), null)).isTrue();
	}

	@Test
	void countsWorkingDaysAndFindsTheNextOneAcrossWeekendAndHoliday() {
		Fixture f = setUp("wd-count", MON_TO_FRI);
		entry(f, null, LocalDate.of(2026, 1, 12), "Holiday", WorkingCalendarEntryType.HOLIDAY); // Monday

		// Fri 9 (working), Sat 10 + Sun 11 (weekly off), Mon 12 (holiday), Tue 13 (working)
		assertThat(workingDayService.countWorkingDays(LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 13), null)).isEqualTo(2);
		assertThat(workingDayService.nextWorkingDay(LocalDate.of(2026, 1, 10), null)).isEqualTo(LocalDate.of(2026, 1, 13));
		assertThat(workingDayService.nextWorkingDay(LocalDate.of(2026, 1, 9), null)).isEqualTo(LocalDate.of(2026, 1, 9));
	}
}
