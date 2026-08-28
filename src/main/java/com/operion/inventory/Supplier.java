package com.operion.inventory;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Vendor master data for GitHub #50 - deliberately just an address book row (name,
 * contact, status), not the start of a procurement workflow. No Purchase Order/Goods
 * Receipt exists yet and shouldn't be assumed from this entity's presence - see
 * ai-context/erp-system-plan.md §3.3's explicit "don't build a warehouse/procurement
 * system unless a school actually needs it" warning, which this ticket was scoped
 * against (cheap/low-risk regardless of whether Milestone 6's PO workflow ever ships).
 * Same status-enum-not-hard-delete pattern as ItemCategory/Item/Department.
 */
@Getter
@Entity
@Table(name = "suppliers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	private String contactPerson;

	private String phone;

	private String email;

	private String address;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupplierStatus status;

	public Supplier(String name, String contactPerson, String phone, String email, String address) {
		this.name = name;
		this.contactPerson = contactPerson;
		this.phone = phone;
		this.email = email;
		this.address = address;
		this.status = SupplierStatus.ACTIVE;
	}

	public void changeStatus(SupplierStatus status) {
		this.status = status;
	}
}
