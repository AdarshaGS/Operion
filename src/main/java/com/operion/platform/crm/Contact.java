package com.operion.platform.crm;

import com.operion.common.persistence.PlatformPlane;
import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A named person on a lead or an organisation - exactly one of leadId/organisationId is set
 * (a DB check constraint backs up {@link CrmOwner}'s service-level check). */
@Getter
@Entity
@Table(name = "crm_contacts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@PlatformPlane
public class Contact extends BaseEntity {

	@Column(name = "lead_id", updatable = false)
	private Long leadId;

	@Column(name = "organisation_id", updatable = false)
	private Long organisationId;

	@Column(nullable = false)
	private String name;

	@Column(name = "role_title")
	private String role;

	private String email;

	private String phone;

	public Contact(CrmOwner owner, String name, String role, String email, String phone) {
		this.leadId = owner.leadId();
		this.organisationId = owner.organisationId();
		update(name, role, email, phone);
	}

	public void update(String name, String role, String email, String phone) {
		this.name = name;
		this.role = role;
		this.email = email;
		this.phone = phone;
	}
}
