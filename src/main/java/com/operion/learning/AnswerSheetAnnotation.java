package com.operion.learning;

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

/** A mark on a page. {@code x}/{@code y} are fractions (0..1) of the page image. */
@Getter
@Entity
@Table(name = "answer_sheet_annotations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerSheetAnnotation extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "page_id")
	private AnswerSheetPage page;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnnotationKind kind;

	@Column(name = "pos_x", nullable = false)
	private double x;

	@Column(name = "pos_y", nullable = false)
	private double y;

	private String label;

	private Double marks;

	public AnswerSheetAnnotation(AnswerSheetPage page, AnnotationKind kind, double x, double y, String label, Double marks) {
		this.page = page;
		this.kind = kind;
		this.x = x;
		this.y = y;
		this.label = label;
		this.marks = marks;
	}
}
