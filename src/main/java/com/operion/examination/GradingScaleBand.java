package com.operion.examination;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * No upper bound stored - GradingScaleService resolves a percentage to the band with the
 * highest minPercentage it meets or exceeds (checked in descending order), which avoids
 * needing to validate bands don't overlap at creation time. At least one band should have
 * minPercentage 0 to guarantee coverage.
 */
@Getter
@Entity
@Table(name = "grading_scale_bands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingScaleBand extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "grading_scale_id")
	private GradingScale gradingScale;

	@Column(nullable = false, length = 10)
	private String grade;

	@Column(name = "min_percentage", nullable = false)
	private Double minPercentage;

	/** Nullable. */
	private String remark;

	public GradingScaleBand(GradingScale gradingScale, String grade, Double minPercentage, String remark) {
		this.gradingScale = gradingScale;
		this.grade = grade;
		this.minPercentage = minPercentage;
		this.remark = remark;
	}
}
