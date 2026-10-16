package com.operion.onboarding;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import org.springframework.stereotype.Service;

/**
 * The School Starter Pack's module selection (GitHub #198 follow-up): turns a few plain-language
 * answers about what the school does into the set of modules to switch on, then applies the
 * setup template for exactly that set. The questions and their module mapping are data
 * ({@link SchoolStarterNeed}); only the dependency rules between modules live in code
 * ({@link AppModule#requires()}), so an answer can never leave a module on without what it needs.
 *
 * Nothing is written until {@link #apply} is called with answers - {@link #recommend} is a pure
 * preview. Choosing modules is possible once per organisation, while it has no module settings;
 * after that the platform owner owns the switches, as before.
 */
@Service
public class SchoolStarterPackService {

	static final String NOTE = "Set by School Starter Pack";

	private final SchoolStarterNeedRepository needRepository;
	private final SchoolTemplateService schoolTemplateService;
	private final ModuleAccessService moduleAccessService;

	public SchoolStarterPackService(SchoolStarterNeedRepository needRepository, SchoolTemplateService schoolTemplateService,
			ModuleAccessService moduleAccessService) {
		this.needRepository = needRepository;
		this.schoolTemplateService = schoolTemplateService;
		this.moduleAccessService = moduleAccessService;
	}

	public Needs needs() {
		List<Question> questions = needRepository.findAllByOrderByDisplayOrderAsc().stream().filter(need -> !need.isBaseline())
				.map(need -> new Question(need.getNeedKey(), need.getLabel(), need.getHelpText(), need.isDefaultYes())).toList();
		return new Needs(questions, canSelectModules());
	}

	/** Pure preview: which modules the answers would turn on or off, and why. Unanswered questions use their default. */
	public Recommendation recommend(Map<String, Boolean> answers) {
		List<SchoolStarterNeed> needs = needRepository.findAllByOrderByDisplayOrderAsc();
		rejectUnknownKeys(answers, needs);

		Map<AppModule, String> reasons = new LinkedHashMap<>();
		Set<AppModule> direct = EnumSet.noneOf(AppModule.class);
		Set<AppModule> locked = EnumSet.noneOf(AppModule.class);
		for (SchoolStarterNeed need : needs) {
			if (need.isBaseline()) {
				for (AppModule module : need.modules()) {
					direct.add(module);
					locked.add(module);
					reasons.putIfAbsent(module, need.getLabel());
				}
			} else if (answers.getOrDefault(need.getNeedKey(), need.isDefaultYes())) {
				for (AppModule module : need.modules()) {
					direct.add(module);
					reasons.putIfAbsent(module, "You need this: " + need.getLabel());
				}
			}
		}
		for (AppModule module : direct) {
			for (AppModule required : AppModule.withDependencies(EnumSet.of(module))) {
				if (!direct.contains(required)) {
					reasons.putIfAbsent(required, "Required by " + module.label());
					locked.add(required);
				}
			}
		}
		Set<AppModule> enabled = AppModule.withDependencies(direct);

		List<ModuleChoice> choices = java.util.Arrays.stream(AppModule.values())
				.map(module -> new ModuleChoice(module, enabled.contains(module), locked.contains(module),
						enabled.contains(module) ? reasons.get(module) : declinedReason(module, needs)))
				.toList();
		return new Recommendation(choices);
	}

	public SchoolTemplateStatus preview() {
		Long organisationId = TenantContext.getOrganisationId();
		return schoolTemplateService.preview(organisationId == null ? Set.of() : moduleAccessService.disabledModules(organisationId));
	}

	/**
	 * Applies the setup template. With answers (and no module settings yet) it also records the
	 * recommended module set; without answers it applies for whatever is currently enabled and
	 * touches no module setting. The template goes first: it is idempotent, so a failure on the
	 * settings write can simply be retried.
	 */
	public SchoolTemplateResult apply(Map<String, Boolean> answers) {
		Long organisationId = TenantContext.getOrganisationId();
		if (answers == null) {
			return schoolTemplateService.apply(moduleAccessService.disabledModules(organisationId));
		}
		if (moduleAccessService.hasHistory(organisationId)) {
			throw new IllegalStateException("Modules were already configured for this organisation; only the platform owner can change them now");
		}
		EnumSet<AppModule> enabled = EnumSet.noneOf(AppModule.class);
		recommend(answers).modules().stream().filter(ModuleChoice::enabled).forEach(choice -> enabled.add(choice.module()));
		Set<AppModule> disabled = EnumSet.complementOf(enabled);

		SchoolTemplateResult result = schoolTemplateService.apply(disabled);
		moduleAccessService.applyInitialSelection(organisationId, enabled, NOTE);
		return result;
	}

	private boolean canSelectModules() {
		Long organisationId = TenantContext.getOrganisationId();
		return organisationId != null && !moduleAccessService.hasHistory(organisationId);
	}

	private String declinedReason(AppModule module, List<SchoolStarterNeed> needs) {
		return needs.stream().filter(need -> !need.isBaseline() && need.modules().contains(module)).findFirst()
				.map(need -> "You said no to: " + need.getLabel()).orElse("Not part of your selection");
	}

	private void rejectUnknownKeys(Map<String, Boolean> answers, List<SchoolStarterNeed> needs) {
		Set<String> known = needs.stream().filter(need -> !need.isBaseline()).map(SchoolStarterNeed::getNeedKey)
				.collect(java.util.stream.Collectors.toSet());
		for (String key : answers.keySet()) {
			if (!known.contains(key)) {
				throw new IllegalArgumentException("Unknown starter pack question: " + key);
			}
		}
	}

	public record Question(String key, String label, String helpText, boolean defaultYes) {
	}

	/** {@code canSelectModules} is false once the organisation has module settings - the UI then skips the questions. */
	public record Needs(List<Question> questions, boolean canSelectModules) {
	}

	public record ModuleChoice(AppModule module, boolean enabled, boolean locked, String reason) {
	}

	public record Recommendation(List<ModuleChoice> modules) {
	}
}
