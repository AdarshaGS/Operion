package com.operion.privacy;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An org-defined thing a person can agree to (e.g. "Photo publication") - the catalog ships
 * empty. {@link #code} is the fixed identity; name/description are editable. */
@Getter
@Entity
@Table(name = "consent_purposes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentPurpose extends TenantScopedEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	/** Nullable. */
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConsentPurposeStatus status;

	public ConsentPurpose(String code, String name, String description) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.status = ConsentPurposeStatus.ACTIVE;
	}

	public void update(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public void changeStatus(ConsentPurposeStatus status) {
		this.status = status;
	}
}
