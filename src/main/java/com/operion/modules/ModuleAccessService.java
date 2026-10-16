package com.operion.modules;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.operion.organisation.OrganisationRepository;
import org.springframework.stereotype.Service;
import com.operion.common.persistence.PlatformTransactional;

/**
 * Current module state per organisation, folded from the insert-only
 * organisation_module_settings history: the latest row per module wins and a module with
 * no row falls back to {@link AppModule#defaultEnabled()} (on, except Transport,
 * Communication and Messaging, which stay off until switched on). Read on every request that reaches a module-owned handler (one small
 * indexed query, same cost class as the permission lookup beside it), so a platform-owner
 * toggle takes effect on the very next request with no cache to invalidate.
 */
@Service
public class ModuleAccessService {

	private final OrganisationModuleSettingRepository settingRepository;
	private final OrganisationRepository organisationRepository;

	public ModuleAccessService(OrganisationModuleSettingRepository settingRepository, OrganisationRepository organisationRepository) {
		this.settingRepository = settingRepository;
		this.organisationRepository = organisationRepository;
	}

	@PlatformTransactional(readOnly = true)
	public Set<AppModule> disabledModules(Long organisationId) {
		Set<AppModule> disabled = EnumSet.noneOf(AppModule.class);
		for (AppModule module : AppModule.values()) {
			if (!module.defaultEnabled()) {
				disabled.add(module);
			}
		}
		for (OrganisationModuleSetting setting : settingRepository.findByOrganisationIdOrderByIdAsc(organisationId)) {
			if (setting.isEnabled()) {
				disabled.remove(setting.getModule());
			} else {
				disabled.add(setting.getModule());
			}
		}
		return disabled;
	}

	@PlatformTransactional(readOnly = true)
	public boolean isEnabled(Long organisationId, AppModule module) {
		return !disabledModules(organisationId).contains(module);
	}

	@PlatformTransactional(readOnly = true)
	public List<ModuleState> states(Long organisationId) {
		requireOrganisation(organisationId);
		Set<AppModule> disabled = disabledModules(organisationId);
		return Arrays.stream(AppModule.values()).map(module -> new ModuleState(module, !disabled.contains(module))).toList();
	}

	/** A no-op (no row written) when the module is already in the requested state, so history only records real changes. */
	@PlatformTransactional
	public ModuleState setEnabled(Long organisationId, AppModule module, boolean enabled, String note) {
		requireOrganisation(organisationId);
		if (isEnabled(organisationId, module) != enabled) {
			settingRepository.save(new OrganisationModuleSetting(organisationId, module, enabled, note));
		}
		return new ModuleState(module, enabled);
	}

	@PlatformTransactional(readOnly = true)
	public boolean hasHistory(Long organisationId) {
		return !settingRepository.findByOrganisationIdOrderByIdAsc(organisationId).isEmpty();
	}

	/**
	 * The one write a non-platform caller can trigger: the School Starter Pack choosing the
	 * initial module set at onboarding. Writes an explicit row for every module (so a module
	 * added to the catalogue later does not silently switch on for a pack-configured school)
	 * and refuses once any setting exists - after that, changes stay with the platform owner.
	 */
	@PlatformTransactional
	public void applyInitialSelection(Long organisationId, Set<AppModule> enabled, String note) {
		requireOrganisation(organisationId);
		if (hasHistory(organisationId)) {
			throw new IllegalStateException("Modules were already configured for this organisation; only the platform owner can change them now");
		}
		Set<AppModule> selected = AppModule.withDependencies(enabled);
		for (AppModule module : AppModule.values()) {
			settingRepository.save(new OrganisationModuleSetting(organisationId, module, selected.contains(module), note));
		}
	}

	@PlatformTransactional(readOnly = true)
	public List<OrganisationModuleSetting> history(Long organisationId) {
		requireOrganisation(organisationId);
		return settingRepository.findByOrganisationIdOrderByIdDesc(organisationId);
	}

	private void requireOrganisation(Long organisationId) {
		if (!organisationRepository.existsById(organisationId)) {
			throw new IllegalArgumentException("No organisation with id " + organisationId);
		}
	}

	public record ModuleState(AppModule module, boolean enabled) {
	}
}
