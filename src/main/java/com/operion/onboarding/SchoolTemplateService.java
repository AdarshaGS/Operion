package com.operion.onboarding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.operion.academic.AcademicService;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.authorization.RoleService;
import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationService.BandInput;
import com.operion.examination.GradingScaleRepository;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeCategoryType;
import com.operion.finance.FeeService;
import com.operion.inventory.InventoryService;
import com.operion.inventory.ItemCategory;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The opt-in "School setup template" applied from Organisation Settings - never run
 * automatically during provisioning (see OrganisationService.seedDefaultRoles(), GitHub
 * #92: Operion is multi-industry, so School-specific catalog rows are only ever created on
 * an explicit admin action). Reads its item list from {@link SchoolTemplateItemService} -
 * an editable, per-organisation catalog, not a hardcoded list - so admins can add or remove
 * what a future apply() will create without any code change. Every category is additive and
 * skip-if-exists (by name, and by code where the DB enforces a unique code), so applying the
 * template more than once is always a safe no-op for anything already present.
 */
@Service
public class SchoolTemplateService {

	private static final String OWNER_ROLE_NAME = "Owner";
	private static final String TRANSPORT_FEE_CATEGORY_TYPE = "TRANSPORT";
	private static final String DEFAULT_GRADING_SCALE_NAME = "Standard Grading Scale";

	private final SchoolTemplateItemService schoolTemplateItemService;
	private final AcademicService academicService;
	private final GradeLevelRepository gradeLevelRepository;
	private final DepartmentRepository departmentRepository;
	private final DesignationRepository designationRepository;
	private final RoleRepository roleRepository;
	private final RoleService roleService;
	private final PermissionRepository permissionRepository;
	private final FeeService feeService;
	private final FeeCategoryRepository feeCategoryRepository;
	private final InventoryService inventoryService;
	private final ItemCategoryRepository itemCategoryRepository;
	private final ExaminationService examinationService;
	private final GradingScaleRepository gradingScaleRepository;

	public SchoolTemplateService(SchoolTemplateItemService schoolTemplateItemService, AcademicService academicService,
			GradeLevelRepository gradeLevelRepository, DepartmentRepository departmentRepository, DesignationRepository designationRepository,
			RoleRepository roleRepository, RoleService roleService, PermissionRepository permissionRepository,
			FeeService feeService, FeeCategoryRepository feeCategoryRepository, InventoryService inventoryService,
			ItemCategoryRepository itemCategoryRepository, ExaminationService examinationService,
			GradingScaleRepository gradingScaleRepository) {
		this.schoolTemplateItemService = schoolTemplateItemService;
		this.academicService = academicService;
		this.gradeLevelRepository = gradeLevelRepository;
		this.departmentRepository = departmentRepository;
		this.designationRepository = designationRepository;
		this.roleRepository = roleRepository;
		this.roleService = roleService;
		this.permissionRepository = permissionRepository;
		this.feeService = feeService;
		this.feeCategoryRepository = feeCategoryRepository;
		this.inventoryService = inventoryService;
		this.itemCategoryRepository = itemCategoryRepository;
		this.examinationService = examinationService;
		this.gradingScaleRepository = gradingScaleRepository;
	}

	public SchoolTemplateStatus preview() {
		Set<String> existingGrades = normalizedNames(gradeLevelRepository.findAll().stream().map(GradeLevel::getName));
		Set<String> existingDepartments = normalizedNames(departmentRepository.findAll().stream().map(Department::getName));
		Set<String> existingDesignations = normalizedNames(designationRepository.findAll().stream().map(Designation::getName));
		Set<String> existingRoles = normalizedNames(roleRepository.findAll().stream().map(Role::getName));
		Set<String> existingFeeCategories = normalizedNames(feeCategoryRepository.findAll().stream().map(FeeCategory::getName));
		Set<String> existingItemCategories = normalizedNames(itemCategoryRepository.findAll().stream().map(ItemCategory::getName));

		List<SchoolTemplateCategoryStatus> roleStatuses = new ArrayList<>();
		roleStatuses.add(new SchoolTemplateCategoryStatus("Organisation Owner", roleRepository.findByName(OWNER_ROLE_NAME).isPresent()));
		roleStatuses.addAll(itemStatuses(SchoolTemplateItemCategory.ROLE, existingRoles));

		return new SchoolTemplateStatus(
				itemStatuses(SchoolTemplateItemCategory.GRADE, existingGrades),
				itemStatuses(SchoolTemplateItemCategory.DEPARTMENT, existingDepartments),
				itemStatuses(SchoolTemplateItemCategory.DESIGNATION, existingDesignations),
				roleStatuses,
				itemStatuses(SchoolTemplateItemCategory.FEE_CATEGORY, existingFeeCategories),
				itemStatuses(SchoolTemplateItemCategory.ITEM_CATEGORY, existingItemCategories),
				!gradingScaleRepository.findAll().isEmpty());
	}

	@Transactional
	public SchoolTemplateResult apply() {
		SchoolTemplateResult result = new SchoolTemplateResult();
		applyGrades(result);
		applyDepartments(result);
		applyDesignations(result);
		applyRoles(result);
		applyFeeCategories(result);
		applyItemCategories(result);
		applyGradingScale(result);
		return result;
	}

	/** Sequence order is always assigned as (current max + 1) at apply-time, in template
	 * order - never the item's own stored sequenceOrder - so a newly-created grade can
	 * never collide with grade_levels' UNIQUE (organisation_id, sequence_order) constraint
	 * against a grade the org already created by hand with an overlapping number. */
	private void applyGrades(SchoolTemplateResult result) {
		List<GradeLevel> existingGrades = gradeLevelRepository.findAll();
		Set<String> existingNames = normalizedNames(existingGrades.stream().map(GradeLevel::getName));
		int nextSequenceOrder = existingGrades.stream().mapToInt(GradeLevel::getSequenceOrder).max().orElse(-1) + 1;

		for (SchoolTemplateItem item : schoolTemplateItemService.list(SchoolTemplateItemCategory.GRADE)) {
			if (existingNames.contains(normalize(item.getName()))) {
				result.getGradesSkipped().add(item.getName());
				continue;
			}
			academicService.createGradeLevel(item.getName(), nextSequenceOrder, item.getStage());
			nextSequenceOrder++;
			result.getGradesCreated().add(item.getName());
		}
	}

	private void applyDepartments(SchoolTemplateResult result) {
		Set<String> existing = normalizedNames(departmentRepository.findAll().stream().map(Department::getName));
		for (SchoolTemplateItem item : schoolTemplateItemService.list(SchoolTemplateItemCategory.DEPARTMENT)) {
			if (existing.contains(normalize(item.getName()))) {
				result.getDepartmentsSkipped().add(item.getName());
			} else {
				departmentRepository.save(new Department(item.getName()));
				result.getDepartmentsCreated().add(item.getName());
			}
		}
	}

	private void applyDesignations(SchoolTemplateResult result) {
		Set<String> existing = normalizedNames(designationRepository.findAll().stream().map(Designation::getName));
		for (SchoolTemplateItem item : schoolTemplateItemService.list(SchoolTemplateItemCategory.DESIGNATION)) {
			if (existing.contains(normalize(item.getName()))) {
				result.getDesignationsSkipped().add(item.getName());
			} else {
				designationRepository.save(new Designation(item.getName()));
				result.getDesignationsCreated().add(item.getName());
			}
		}
	}

	private void applyRoles(SchoolTemplateResult result) {
		if (roleRepository.findByName(OWNER_ROLE_NAME).isPresent()) {
			result.getRolesSkipped().add("Organisation Owner");
		}

		Set<String> knownPermissionCodes = permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
		Set<String> existing = normalizedNames(roleRepository.findAll().stream().map(Role::getName));
		for (SchoolTemplateItem item : schoolTemplateItemService.list(SchoolTemplateItemCategory.ROLE)) {
			if (existing.contains(normalize(item.getName()))) {
				result.getRolesSkipped().add(item.getName());
				continue;
			}
			Set<String> grantablePermissions = item.permissionCodeSet().stream().filter(knownPermissionCodes::contains).collect(Collectors.toSet());
			roleService.create(item.getName(), item.getDescription(), grantablePermissions);
			result.getRolesCreated().add(item.getName());
		}
	}

	/** Skips on a name OR code collision - fee_categories enforces UNIQUE (organisation_id,
	 * code), so a differently-named existing category reusing a template item's code would
	 * otherwise fail the insert outright instead of being reported as skipped. */
	private void applyFeeCategories(SchoolTemplateResult result) {
		List<FeeCategory> existingCategories = feeCategoryRepository.findAll();
		Set<String> existingNames = normalizedNames(existingCategories.stream().map(FeeCategory::getName));
		Set<String> existingCodes = normalizedNames(existingCategories.stream().map(FeeCategory::getCode));
		for (SchoolTemplateItem item : schoolTemplateItemService.list(SchoolTemplateItemCategory.FEE_CATEGORY)) {
			if (existingNames.contains(normalize(item.getName())) || existingCodes.contains(normalize(item.getCode()))) {
				result.getFeeCategoriesSkipped().add(item.getName());
				continue;
			}
			FeeCategoryType categoryType =
					TRANSPORT_FEE_CATEGORY_TYPE.equals(item.getCategoryType()) ? FeeCategoryType.TRANSPORT : FeeCategoryType.GENERAL;
			feeService.createCategory(item.getCode(), item.getName(), item.getDescription(), categoryType);
			result.getFeeCategoriesCreated().add(item.getName());
		}
	}

	/** Same name-or-code guard as applyFeeCategories - item_categories enforces the same
	 * UNIQUE (organisation_id, code) shape. */
	private void applyItemCategories(SchoolTemplateResult result) {
		List<ItemCategory> existingCategories = itemCategoryRepository.findAll();
		Set<String> existingNames = normalizedNames(existingCategories.stream().map(ItemCategory::getName));
		Set<String> existingCodes = normalizedNames(existingCategories.stream().map(ItemCategory::getCode));
		for (SchoolTemplateItem item : schoolTemplateItemService.list(SchoolTemplateItemCategory.ITEM_CATEGORY)) {
			if (existingNames.contains(normalize(item.getName())) || existingCodes.contains(normalize(item.getCode()))) {
				result.getItemCategoriesSkipped().add(item.getName());
				continue;
			}
			inventoryService.createCategory(item.getCode(), item.getName(), item.getDescription());
			result.getItemCategoriesCreated().add(item.getName());
		}
	}

	/** Seeds the first grading scale only - GradingScaleController has no default fallback
	 * (unlike ExaminationSettings/DocumentTemplate) and ExaminationService.resolveGrade()
	 * needs an actual persisted scale to produce a letter grade at all. Not a merge: if an
	 * org already has any scale, it's left alone. If every GRADING_BAND item has been
	 * removed from the org's template catalog, there's nothing to seed. */
	private void applyGradingScale(SchoolTemplateResult result) {
		if (!gradingScaleRepository.findAll().isEmpty()) {
			result.setGradingScaleAlreadyExists(true);
			return;
		}
		List<SchoolTemplateItem> bandItems = schoolTemplateItemService.list(SchoolTemplateItemCategory.GRADING_BAND);
		if (bandItems.isEmpty()) {
			return;
		}
		List<BandInput> bands = bandItems.stream().map(item -> new BandInput(item.getName(), item.getMinPercentage(), item.getRemark())).toList();
		examinationService.createGradingScale(DEFAULT_GRADING_SCALE_NAME, true, bands);
		result.setGradingScaleCreated(true);
	}

	private List<SchoolTemplateCategoryStatus> itemStatuses(SchoolTemplateItemCategory category, Set<String> existingNormalizedNames) {
		return schoolTemplateItemService.list(category).stream()
				.map(item -> new SchoolTemplateCategoryStatus(item.getName(), existingNormalizedNames.contains(normalize(item.getName()))))
				.toList();
	}

	private Set<String> normalizedNames(Stream<String> names) {
		return names.map(this::normalize).collect(Collectors.toSet());
	}

	private String normalize(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
