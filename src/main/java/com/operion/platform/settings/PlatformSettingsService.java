package com.operion.platform.settings;

import com.operion.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSettingsService {

	private final PlatformSettingRepository platformSettingRepository;
	private final AuditLogService auditLogService;

	public PlatformSettingsService(PlatformSettingRepository platformSettingRepository, AuditLogService auditLogService) {
		this.platformSettingRepository = platformSettingRepository;
		this.auditLogService = auditLogService;
	}

	/** Self-heals if the singleton row is missing (e.g. a test schema built straight from
	 * entities, Flyway disabled - V70's seed insert never runs there) rather than requiring
	 * every environment to replicate the production seed. */
	public PlatformSetting current() {
		return platformSettingRepository.findFirstByOrderByIdAsc()
				.orElseGet(() -> platformSettingRepository.save(new PlatformSetting(PlatformSetting.DEFAULT_TRIAL_DAYS)));
	}

	@Transactional
	public PlatformSetting updateTrialDays(int trialDays) {
		PlatformSetting setting = current();
		int previous = setting.getTrialDays();
		setting.setTrialDays(trialDays);
		setting = platformSettingRepository.save(setting);
		auditLogService.record("PlatformSetting", setting.getId(), "TRIAL_DAYS_CHANGE", previous, trialDays);
		return setting;
	}
}
