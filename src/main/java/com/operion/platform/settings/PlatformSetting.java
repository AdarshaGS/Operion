package com.operion.platform.settings;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Singleton settings row (exactly one, its id whatever V70's seed insert or
 * PlatformSettingsService's self-heal happened to assign it - see
 * PlatformSettingRepository.findFirstByOrderByIdAsc()) - not organisation-scoped, global
 * across the whole platform. One column per setting, same "concrete columns, not a
 * generic key-value store" convention as the rest of this schema; see V70's comment.
 */
@Getter
@Entity
@Table(name = "platform_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformSetting extends BaseEntity {

	public static final int DEFAULT_TRIAL_DAYS = 14;

	@Column(name = "trial_days", nullable = false)
	private int trialDays;

	PlatformSetting(int trialDays) {
		this.trialDays = trialDays;
	}

	public void setTrialDays(int trialDays) {
		this.trialDays = trialDays;
	}
}
