package com.operion.timetable;

import com.operion.academic.SchoolClass;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Says a class follows a period template rather than the default day. Kept here, not on the class,
 * so the academic package does not depend on the timetable. No row means the default day.
 */
@Getter
@Entity
@Table(name = "class_period_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassPeriodTemplate extends TenantScopedEntity {

	@OneToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@ManyToOne(optional = false)
	@JoinColumn(name = "template_id")
	private PeriodTemplate template;

	public ClassPeriodTemplate(SchoolClass schoolClass, PeriodTemplate template) {
		this.schoolClass = schoolClass;
		this.template = template;
	}

	public void useTemplate(PeriodTemplate template) {
		this.template = template;
	}
}
