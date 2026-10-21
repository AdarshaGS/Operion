package com.operion.performance;

import java.time.LocalDate;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A review window: ratings and goals belong to one cycle, and its dates define the period the scorecard is read over. */
@Getter
@Entity
@Table(name = "appraisal_cycles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraisalCycle extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AppraisalCycleStatus status;

	public AppraisalCycle(String name, LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			throw new InvalidRequestException("The cycle end cannot be before its start");
		}
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
		this.status = AppraisalCycleStatus.OPEN;
	}

	public boolean isOpen() {
		return status == AppraisalCycleStatus.OPEN;
	}

	public void close() {
		if (!isOpen()) {
			throw new InvalidRequestException("The cycle is already closed");
		}
		this.status = AppraisalCycleStatus.CLOSED;
	}

	public void requireOpen() {
		if (!isOpen()) {
			throw new InvalidRequestException("The cycle " + name + " is closed");
		}
	}
}
