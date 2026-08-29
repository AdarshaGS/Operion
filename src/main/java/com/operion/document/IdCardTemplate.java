package com.operion.document;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An org-scoped ID card design: element positions/bindings as JSON. A deliberate
 * exception to the project's usual avoid-JSON-columns convention - layout data is
 * genuinely freeform (arbitrary element count/type/position), unlike the rest of the
 * schema. Opaque to everything except IdCardTemplateService.render(), which parses it to
 * resolve DATA_FIELD/QR_CODE bindings against a real student. Per #33.
 */
@Getter
@Setter
@Entity
@Table(name = "id_card_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdCardTemplate extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "width_mm", nullable = false)
	private double widthMm;

	@Column(name = "height_mm", nullable = false)
	private double heightMm;

	@Column(name = "layout_json", nullable = false, columnDefinition = "TEXT")
	private String layoutJson;

	public IdCardTemplate(String name, double widthMm, double heightMm, String layoutJson) {
		this.name = name;
		this.widthMm = widthMm;
		this.heightMm = heightMm;
		this.layoutJson = layoutJson;
	}
}
