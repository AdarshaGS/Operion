package com.operion.timetable;

import com.operion.academic.Section;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Marks a section's timetable as published: portals and teachers' own views show only published
 * timetables. The row's createdAt/createdBy are the publish time and publisher; unpublishing deletes it.
 * Edits to a published timetable go live straight away - publishing gates visibility, it is not a snapshot.
 */
@Getter
@Entity
@Table(name = "timetable_publications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetablePublication extends TenantScopedEntity {

	@OneToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	public TimetablePublication(Section section) {
		this.section = section;
	}
}
