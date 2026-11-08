package com.operion.academic.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.SchoolClass;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.Campus;
import org.junit.jupiter.api.Test;

class SchoolClassResponseTest {

	@Test
	void carriesTheCampusNameSoPickersCanTellPerCampusClassesApart() {
		AcademicYear year = new AcademicYear("2026-27", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31));
		Campus campus = new Campus("Peresandra Branch", "PB");
		GradeLevel grade = new GradeLevel("LKG", 1, "PRE_PRIMARY");

		SchoolClassResponse response = SchoolClassResponse.from(new SchoolClass(year, campus, grade, null));

		assertThat(response.campusName()).isEqualTo("Peresandra Branch");
		assertThat(response.displayName()).isNull();
	}
}
