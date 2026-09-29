package com.operion.inventory;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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
import lombok.Setter;

/**
 * A single, individually-identified physical asset (laptop, projector, furniture) -
 * campus-scoped, one row per physical unit, distinct from the bulk-stock ledger
 * (StockEntry/StockIssue) the way BookCopy is distinct from Book (#177). item links
 * back to the catalog for type/category; assetTag is the asset's own serial/tag,
 * independent of Item.code. assignedPerson and assignedLocation are both independently
 * optional and not mutually exclusive - an asset can sit in a location and also be
 * checked out to a person. Never hard-deleted; status covers its lifecycle instead.
 */
@Getter
@Entity
@Table(name = "assets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "asset_tag", nullable = false)
	private String assetTag;

	/** Nullable - the person currently holding/using this asset, if any. */
	@Setter
	@ManyToOne
	@JoinColumn(name = "assigned_person_id")
	private Person assignedPerson;

	/** Nullable - free text, same convention as Item.storageLocation. */
	@Setter
	@Column(name = "assigned_location")
	private String assignedLocation;

	/** Nullable. */
	@Column(name = "purchase_date")
	private LocalDate purchaseDate;

	/** Nullable. */
	@Column(name = "warranty_expiry_date")
	private LocalDate warrantyExpiryDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AssetStatus status;

	public Asset(Item item, Campus campus, String assetTag, LocalDate purchaseDate, LocalDate warrantyExpiryDate) {
		this.item = item;
		this.campus = campus;
		this.assetTag = assetTag;
		this.purchaseDate = purchaseDate;
		this.warrantyExpiryDate = warrantyExpiryDate;
		this.status = AssetStatus.ACTIVE;
	}

	public void changeStatus(AssetStatus status) {
		this.status = status;
	}
}
