package com.operion.hr;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
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
 * Insert-only exit event log, deliberately with no one-per-staff uniqueness - a staff
 * member could resign and later be re-hired, same convention as StudentExit.
 */
@Getter
@Entity
@Table(name = "staff_exits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffExit extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	@Enumerated(EnumType.STRING)
	@Column(name = "exit_type", nullable = false, length = 20)
	private StaffExitType exitType;

	@Column(name = "exit_date", nullable = false)
	private LocalDate exitDate;

	/** Nullable. */
	private String reason;

	/** Nullable - references a User id, no FK by design. */
	@Column(name = "initiated_by")
	private Long initiatedBy;

	public StaffExit(StaffProfile staffProfile, StaffExitType exitType, LocalDate exitDate, String reason, Long initiatedBy) {
		this.staffProfile = staffProfile;
		this.exitType = exitType;
		this.exitDate = exitDate;
		this.reason = reason;
		this.initiatedBy = initiatedBy;
	}
}
