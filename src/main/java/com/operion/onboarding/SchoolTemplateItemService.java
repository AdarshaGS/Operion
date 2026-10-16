package com.operion.onboarding;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the per-organisation, editable "School setup template" catalog. A fresh org has no
 * rows until the first read, at which point {@link #ensureSeeded()} copies the global,
 * migration-seeded {@link SchoolTemplateMasterItem} catalog into that org's own rows -
 * no default data is embedded in this code, only the one-time copy operation. From then
 * on, every add/edit/remove is a plain database change through this service.
 */
@Service
public class SchoolTemplateItemService {

	private final SchoolTemplateItemRepository repository;
	private final SchoolTemplateMasterItemRepository masterRepository;

	public SchoolTemplateItemService(SchoolTemplateItemRepository repository, SchoolTemplateMasterItemRepository masterRepository) {
		this.repository = repository;
		this.masterRepository = masterRepository;
	}

	/**
	 * The Settings panel loads all 7 categories at once, firing one GET per category -
	 * each one calls this. {@code synchronized} closes the common case (serializes these
	 * within one JVM instance, the same "single-instance, documented trade-off" already
	 * accepted by LoginAttemptService). It alone isn't watertight against the transaction
	 * boundary (the lock releases before the transactional proxy actually commits), so
	 * the (organisation_id, category, name) UNIQUE constraint is the real backstop: if two
	 * requests still race past both the lock and the empty-check, the loser's insert fails
	 * fast and is treated as "someone else already seeded this," not an error.
	 */
	public void ensureSeeded() {
		try {
			seedIfEmpty();
		} catch (DataIntegrityViolationException alreadySeededConcurrently) {
			// Another request won the race and committed first - this organisation's
			// catalog exists either way, so there's nothing left to do here.
		}
	}

	@Transactional
	synchronized void seedIfEmpty() {
		if (!repository.findAll().isEmpty()) {
			return;
		}
		List<SchoolTemplateItem> copies = masterRepository.findAll().stream()
				.map(master -> SchoolTemplateItem.of(master.getCategory(), master.getName(), master.getCode(), master.getDescription(),
						master.getSequenceOrder(), master.getStage(), master.getCategoryType(), master.getMinPercentage(),
						master.getRemark(), master.getPermissionCodes()).taggedWith(master.getModuleCode())
						.withItemDetails(master.getUnit(), master.getItemCategoryCode()))
				.toList();
		repository.saveAll(copies);
	}

	public List<SchoolTemplateItem> list(SchoolTemplateItemCategory category) {
		ensureSeeded();
		return repository.findByCategoryOrderBySequenceOrderAscNameAsc(category);
	}

	@Transactional
	public SchoolTemplateItem create(SchoolTemplateItemCategory category, String name, String code, String description,
			Integer sequenceOrder, String stage, String categoryType, Double minPercentage, String remark, Set<String> permissionCodes,
			String unit, String itemCategoryCode) {
		SchoolTemplateItem item = SchoolTemplateItem.of(
				category, name, code, description, sequenceOrder, stage, categoryType, minPercentage, remark, join(permissionCodes))
				.withItemDetails(unit, itemCategoryCode);
		SchoolTemplateItem saved = repository.save(item);
		if (category == SchoolTemplateItemCategory.GRADE) {
			addClassRowFor(saved);
		}
		return saved;
	}

	@Transactional
	public SchoolTemplateItem update(Long id, String name, String code, String description, Integer sequenceOrder, String stage,
			String categoryType, Double minPercentage, String remark, Set<String> permissionCodes, String unit, String itemCategoryCode) {
		SchoolTemplateItem item = findOrThrow(id);
		String previousName = item.getName();
		item.update(name, code, description, sequenceOrder, stage, categoryType, minPercentage, remark, join(permissionCodes), unit,
				itemCategoryCode);
		SchoolTemplateItem saved = repository.save(item);
		if (saved.getCategory() == SchoolTemplateItemCategory.GRADE && !previousName.equalsIgnoreCase(name)) {
			classRowNamed(previousName).ifPresentOrElse(row -> {
				row.update(name, null, null, saved.getSequenceOrder(), saved.getStage(), null, null, null, null, null, null);
				repository.save(row);
			}, () -> addClassRowFor(saved));
		}
		return saved;
	}

	@Transactional
	public SchoolTemplateItem setIncluded(Long id, boolean included) {
		SchoolTemplateItem item = findOrThrow(id);
		item.setIncluded(included);
		return repository.save(item);
	}

	/** Select-all / select-none for one category's rows. */
	@Transactional
	public List<SchoolTemplateItem> setIncludedForCategory(SchoolTemplateItemCategory category, boolean included) {
		ensureSeeded();
		List<SchoolTemplateItem> rows = repository.findByCategoryOrderBySequenceOrderAscNameAsc(category);
		rows.forEach(row -> row.setIncluded(included));
		return repository.saveAll(rows);
	}

	@Transactional
	public void delete(Long id) {
		SchoolTemplateItem item = findOrThrow(id);
		if (item.getCategory() == SchoolTemplateItemCategory.GRADE) {
			classRowNamed(item.getName()).ifPresent(repository::delete);
		}
		repository.delete(item);
	}

	/** Every grade row has a matching CLASS row of the same name (created ticked), so a grade added in the settings
	 * screen gets a class on apply like the defaults do. The CLASS row can then be unticked on its own. */
	private void addClassRowFor(SchoolTemplateItem grade) {
		if (classRowNamed(grade.getName()).isEmpty()) {
			repository.save(SchoolTemplateItem.of(SchoolTemplateItemCategory.CLASS, grade.getName(), null, null, grade.getSequenceOrder(),
					grade.getStage(), null, null, null, null).taggedWith("ACADEMICS"));
		}
	}

	private java.util.Optional<SchoolTemplateItem> classRowNamed(String name) {
		return repository.findByCategoryOrderBySequenceOrderAscNameAsc(SchoolTemplateItemCategory.CLASS).stream()
				.filter(row -> row.getName().equalsIgnoreCase(name)).findFirst();
	}

	private String join(Set<String> permissionCodes) {
		return permissionCodes == null || permissionCodes.isEmpty() ? null : String.join(",", permissionCodes);
	}

	private SchoolTemplateItem findOrThrow(Long id) {
		return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("No school template item with id " + id));
	}
}
