package com.operion.organisation;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "campuses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campus extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String code;

	@Column(name = "address_line1")
	private String addressLine1;

	@Column(name = "address_line2")
	private String addressLine2;

	private String city;

	private String state;

	private String pincode;

	/** Nullable - falls back to the organisation's default timezone when unset. */
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CampusStatus status;

	public Campus(String name, String code) {
		this.name = name;
		this.code = code;
		this.status = CampusStatus.ACTIVE;
	}
}
