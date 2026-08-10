package com.operion.academic;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Catalog of subjects a school teaches, org-scoped, not year-scoped. */
@Getter
@Entity
@Table(name = "subjects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subject extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	/** Nullable - short code like "MATH", not every school uses one. */
	private String code;

	@Column(name = "is_elective", nullable = false)
	private boolean elective;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubjectStatus status;

	public Subject(String name, String code, boolean elective) {
		this.name = name;
		this.code = code;
		this.elective = elective;
		this.status = SubjectStatus.ACTIVE;
	}

	public void changeStatus(SubjectStatus status) {
		this.status = status;
	}
}
