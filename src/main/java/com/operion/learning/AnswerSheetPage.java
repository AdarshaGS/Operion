package com.operion.learning;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "answer_sheet_pages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerSheetPage extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "sheet_id")
	private AnswerSheet sheet;

	@Column(name = "page_number", nullable = false)
	private int pageNumber;

	@Column(name = "asset_ref", nullable = false)
	private String assetRef;

	public AnswerSheetPage(AnswerSheet sheet, int pageNumber, String assetRef) {
		this.sheet = sheet;
		this.pageNumber = pageNumber;
		this.assetRef = assetRef;
	}
}
