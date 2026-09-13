package com.operion.platform.settings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

	/** Singleton table - looks up "the" row by insertion order rather than assuming it
	 * always lands on PlatformSetting.SINGLETON_ID, so a self-healed row (see
	 * PlatformSettingsService.current()) is found correctly regardless of what id its
	 * auto-increment actually assigned it. */
	Optional<PlatformSetting> findFirstByOrderByIdAsc();
}
