package com.operion.onboarding;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One Structure Setup wizard step the school has completed. Existence of the row is the whole fact; see
 * {@code V29__setup_step_completions.sql}. */
@Getter
@Entity
@Table(name = "setup_step_completions", uniqueConstraints = @UniqueConstraint(columnNames = { "organisation_id", "step_key" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetupStepCompletion extends TenantScopedEntity {

	@Column(name = "step_key", nullable = false, length = 40)
	private String stepKey;

	public SetupStepCompletion(SetupStep step) {
		this.stepKey = step.key();
	}
}
