package com.operion.academic;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.Campus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A GradeLevel instantiated within a year+campus ("Grade 5 at Campus X in AY2025-26").
 * Exists within a year so structure can change year to year without touching prior years.
 */
@Getter
@Entity
@Table(name = "school_classes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolClass extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@ManyToOne(optional = false)
	@JoinColumn(name = "grade_level_id")
	private GradeLevel gradeLevel;

	/** Nullable - falls back to the grade level's name when unset. */
	@Column(name = "display_name")
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SchoolClassStatus status;

	public SchoolClass(AcademicYear academicYear, Campus campus, GradeLevel gradeLevel, String displayName) {
		this.academicYear = academicYear;
		this.campus = campus;
		this.gradeLevel = gradeLevel;
		this.displayName = displayName;
		this.status = SchoolClassStatus.ACTIVE;
	}
}
