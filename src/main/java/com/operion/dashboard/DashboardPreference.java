package com.operion.dashboard;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per (organisation, user) - per-user dismissal state for the dashboard's
 * onboarding widgets (Setup progress, Quick actions). References a User id, no FK by
 * design, same convention as ProfileChangeRequest.requestedBy.
 */
@Getter
@Entity
@Table(name = "dashboard_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardPreference extends TenantScopedEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "setup_progress_dismissed", nullable = false)
	private boolean setupProgressDismissed;

	@Column(name = "quick_actions_dismissed", nullable = false)
	private boolean quickActionsDismissed;

	public DashboardPreference(Long userId) {
		this.userId = userId;
	}

	public void dismissSetupProgress() {
		this.setupProgressDismissed = true;
	}

	public void dismissQuickActions() {
		this.quickActionsDismissed = true;
	}
}
