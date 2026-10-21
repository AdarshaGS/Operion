package com.operion.onboarding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.operion.certificate.CertificateType;
import com.operion.certificate.CertificateTypeRepository;
import com.operion.certificate.CertificateTypeService;
import com.operion.academic.AcademicService;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
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
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.inventory.InventoryService;
import com.operion.inventory.ItemCategory;
import com.operion.inventory.Item;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.inventory.ItemRepository;
import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.CampusStatus;
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
	private static final String DEFAULT_ITEM_UNIT = "PCS";
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
	private final ItemRepository itemRepository;
	private final LeaveTypeRepository leaveTypeRepository;
	private final AcademicYearRepository academicYearRepository;
	private final CampusRepository campusRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final CertificateTypeService certificateTypeService;
	private final CertificateTypeRepository certificateTypeRepository;
	private final ExaminationService examinationService;
	private final GradingScaleRepository gradingScaleRepository;

	public SchoolTemplateService(SchoolTemplateItemService schoolTemplateItemService, AcademicService academicService,
			GradeLevelRepository gradeLevelRepository, DepartmentRepository departmentRepository, DesignationRepository designationRepository,
			RoleRepository roleRepository, RoleService roleService, PermissionRepository permissionRepository,
			FeeService feeService, FeeCategoryRepository feeCategoryRepository, InventoryService inventoryService,
			ItemCategoryRepository itemCategoryRepository, ItemRepository itemRepository, LeaveTypeRepository leaveTypeRepository, ExaminationService examinationService,
			GradingScaleRepository gradingScaleRepository, AcademicYearRepository academicYearRepository, CampusRepository campusRepository,
			SchoolClassRepository schoolClassRepository, SectionRepository sectionRepository, SubjectRepository subjectRepository,
			CertificateTypeService certificateTypeService, CertificateTypeRepository certificateTypeRepository) {
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
		this.itemRepository = itemRepository;
		this.leaveTypeRepository = leaveTypeRepository;
		this.academicYearRepository = academicYearRepository;
		this.campusRepository = campusRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.certificateTypeService = certificateTypeService;
		this.certificateTypeRepository = certificateTypeRepository;
		this.examinationService = examinationService;
		this.gradingScaleRepository = gradingScaleRepository;
	}

	public SchoolTemplateStatus preview() {
		return preview(Set.of());
	}

	/** Rows the admin marked not required, and items belonging to a module in {@code disabled}, are left out of the status, matching what {@link #apply(Set)} would do. */
	public SchoolTemplateStatus preview(Set<AppModule> disabled) {
		Set<String> existingGrades = normalizedNames(gradeLevelRepository.findAll().stream().map(GradeLevel::getName));
		Set<String> existingDepartments = normalizedNames(departmentRepository.findAll().stream().map(Department::getName));
		Set<String> existingDesignations = normalizedNames(designationRepository.findAll().stream().map(Designation::getName));
		Set<String> existingRoles = normalizedNames(roleRepository.findAll().stream().map(Role::getName));
		Set<String> existingFeeCategories = normalizedNames(feeCategoryRepository.findAll().stream().map(FeeCategory::getName));
		Set<String> existingItemCategories = normalizedNames(itemCategoryRepository.findAll().stream().map(ItemCategory::getName));
		Set<String> existingItems = normalizedNames(itemRepository.findAll().stream().map(Item::getName));
		Set<String> existingLeaveTypes = normalizedNames(leaveTypeRepository.findAll().stream().map(LeaveType::getName));

		List<SchoolTemplateCategoryStatus> roleStatuses = new ArrayList<>();
		roleStatuses.add(new SchoolTemplateCategoryStatus("Organisation Owner", roleRepository.findByName(OWNER_ROLE_NAME).isPresent()));
		roleStatuses.addAll(itemStatuses(SchoolTemplateItemCategory.ROLE, existingRoles, disabled));

		return new SchoolTemplateStatus(
				itemStatuses(SchoolTemplateItemCategory.GRADE, existingGrades, disabled),
				itemStatuses(SchoolTemplateItemCategory.DEPARTMENT, existingDepartments, disabled),
				itemStatuses(SchoolTemplateItemCategory.DESIGNATION, existingDesignations, disabled),
				roleStatuses,
				itemStatuses(SchoolTemplateItemCategory.FEE_CATEGORY, existingFeeCategories, disabled),
				itemStatuses(SchoolTemplateItemCategory.ITEM_CATEGORY, existingItemCategories, disabled),
				itemStatuses(SchoolTemplateItemCategory.ITEM, existingItems, disabled),
				itemStatuses(SchoolTemplateItemCategory.ASSET_TYPE, existingItems, disabled),
				itemStatuses(SchoolTemplateItemCategory.LEAVE_TYPE, existingLeaveTypes, disabled),
				classStatuses(disabled),
				sectionStatuses(disabled),
				subjectStatuses(disabled),
				certificateTypeStatuses(disabled),
				!gradingScaleRepository.findAll().isEmpty());
	}

	public SchoolTemplateResult apply() {
		return apply(Set.of());
	}

	/** Same additive, skip-if-exists apply, except items tied to a module in {@code disabled} are not created. */
	@Transactional
	public SchoolTemplateResult apply(Set<AppModule> disabled) {
		SchoolTemplateResult result = new SchoolTemplateResult();
		applyGrades(result, disabled);
		applyClasses(result, disabled);
		applySubjects(result, disabled);
		applyDepartments(result, disabled);
		applyDesignations(result, disabled);
		applyRoles(result, disabled);
		applyFeeCategories(result, disabled);
		applyItemCategories(result, disabled);
		applyInventoryItems(SchoolTemplateItemCategory.ITEM, result.getItemsCreated(), result.getItemsSkipped(), disabled);
		applyInventoryItems(SchoolTemplateItemCategory.ASSET_TYPE, result.getAssetTypesCreated(), result.getAssetTypesSkipped(), disabled);
		applyLeaveTypes(result, disabled);
		applyGradingScale(result, disabled);
		applyCertificateTypes(result, disabled);
		return result;
	}

	/** Sequence order is always assigned as (current max + 1) at apply-time, in template
	 * order - never the item's own stored sequenceOrder - so a newly-created grade can
	 * never collide with grade_levels' UNIQUE (organisation_id, sequence_order) constraint
	 * against a grade the org already created by hand with an overlapping number. */
	private void applyGrades(SchoolTemplateResult result, Set<AppModule> disabled) {
		List<GradeLevel> existingGrades = gradeLevelRepository.findAll();
		Set<String> existingNames = normalizedNames(existingGrades.stream().map(GradeLevel::getName));
		int nextSequenceOrder = existingGrades.stream().mapToInt(GradeLevel::getSequenceOrder).max().orElse(-1) + 1;

		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.GRADE, disabled)) {
			if (existingNames.contains(normalize(item.getName()))) {
				result.getGradesSkipped().add(item.getName());
				continue;
			}
			academicService.createGradeLevel(item.getName(), nextSequenceOrder, item.getStage());
			nextSequenceOrder++;
			result.getGradesCreated().add(item.getName());
		}
	}

	/** Skips on a name OR code collision (code is optional on a subject, so only compared when the row has one). */
	private void applySubjects(SchoolTemplateResult result, Set<AppModule> disabled) {
		List<Subject> existingSubjects = subjectRepository.findAll();
		Set<String> existingNames = normalizedNames(existingSubjects.stream().map(Subject::getName));
		Set<String> existingCodes = normalizedNames(existingSubjects.stream().map(Subject::getCode).filter(Objects::nonNull));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.SUBJECT, disabled)) {
			if (existingNames.contains(normalize(item.getName())) || (item.getCode() != null && existingCodes.contains(normalize(item.getCode())))) {
				result.getSubjectsSkipped().add(item.getName());
				continue;
			}
			subjectRepository.save(new Subject(item.getName(), item.getCode()));
			existingNames.add(normalize(item.getName()));
			if (item.getCode() != null) {
				existingCodes.add(normalize(item.getCode()));
			}
			result.getSubjectsCreated().add(item.getName());
		}
	}

	private List<SchoolTemplateCategoryStatus> subjectStatuses(Set<AppModule> disabled) {
		Set<String> existing = normalizedNames(subjectRepository.findAll().stream().map(Subject::getName));
		return itemStatuses(SchoolTemplateItemCategory.SUBJECT, existing, disabled);
	}

	private void applyDepartments(SchoolTemplateResult result, Set<AppModule> disabled) {
		Set<String> existing = normalizedNames(departmentRepository.findAll().stream().map(Department::getName));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.DEPARTMENT, disabled)) {
			if (existing.contains(normalize(item.getName()))) {
				result.getDepartmentsSkipped().add(item.getName());
			} else {
				departmentRepository.save(new Department(item.getName()));
				result.getDepartmentsCreated().add(item.getName());
			}
		}
	}

	private void applyDesignations(SchoolTemplateResult result, Set<AppModule> disabled) {
		Set<String> existing = normalizedNames(designationRepository.findAll().stream().map(Designation::getName));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.DESIGNATION, disabled)) {
			if (existing.contains(normalize(item.getName()))) {
				result.getDesignationsSkipped().add(item.getName());
			} else {
				designationRepository.save(new Designation(item.getName()));
				result.getDesignationsCreated().add(item.getName());
			}
		}
	}

	private void applyRoles(SchoolTemplateResult result, Set<AppModule> disabled) {
		if (roleRepository.findByName(OWNER_ROLE_NAME).isPresent()) {
			result.getRolesSkipped().add("Organisation Owner");
		}

		Set<String> knownPermissionCodes = permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
		Set<String> existing = normalizedNames(roleRepository.findAll().stream().map(Role::getName));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.ROLE, disabled)) {
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
	private void applyFeeCategories(SchoolTemplateResult result, Set<AppModule> disabled) {
		List<FeeCategory> existingCategories = feeCategoryRepository.findAll();
		Set<String> existingNames = normalizedNames(existingCategories.stream().map(FeeCategory::getName));
		Set<String> existingCodes = normalizedNames(existingCategories.stream().map(FeeCategory::getCode));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.FEE_CATEGORY, disabled)) {
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
	private void applyItemCategories(SchoolTemplateResult result, Set<AppModule> disabled) {
		List<ItemCategory> existingCategories = itemCategoryRepository.findAll();
		Set<String> existingNames = normalizedNames(existingCategories.stream().map(ItemCategory::getName));
		Set<String> existingCodes = normalizedNames(existingCategories.stream().map(ItemCategory::getCode));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.ITEM_CATEGORY, disabled)) {
			if (existingNames.contains(normalize(item.getName())) || existingCodes.contains(normalize(item.getCode()))) {
				result.getItemCategoriesSkipped().add(item.getName());
				continue;
			}
			inventoryService.createCategory(item.getCode(), item.getName(), item.getDescription());
			result.getItemCategoriesCreated().add(item.getName());
		}
	}

	/** Both ITEM (stock-tracked) and ASSET_TYPE (registered unit by unit as Assets) rows become plain catalog
	 * Items - no stock, no Asset units, since a unit needs a campus and a tag only the school can supply. Runs
	 * after applyItemCategories so a category created in the same apply is found. Skips on a name OR code
	 * collision (items enforces UNIQUE (organisation_id, code)), and when the row's parent category is not in the
	 * org any more - the admin removed it, so this never invents one. */
	private void applyInventoryItems(SchoolTemplateItemCategory category, List<String> created, List<String> skipped, Set<AppModule> disabled) {
		Map<String, ItemCategory> categoriesByCode = new HashMap<>();
		itemCategoryRepository.findAll().forEach(existing -> categoriesByCode.put(normalize(existing.getCode()), existing));
		List<Item> existingItems = itemRepository.findAll();
		Set<String> existingNames = normalizedNames(existingItems.stream().map(Item::getName));
		Set<String> existingCodes = normalizedNames(existingItems.stream().map(Item::getCode));
		for (SchoolTemplateItem item : items(category, disabled)) {
			ItemCategory parent = item.getItemCategoryCode() == null ? null : categoriesByCode.get(normalize(item.getItemCategoryCode()));
			if (parent == null || item.getCode() == null || existingNames.contains(normalize(item.getName()))
					|| existingCodes.contains(normalize(item.getCode()))) {
				skipped.add(item.getName());
				continue;
			}
			String unit = item.getUnit() == null || item.getUnit().isBlank() ? DEFAULT_ITEM_UNIT : item.getUnit();
			inventoryService.createItem(parent, item.getCode(), item.getName(), unit, item.getDescription(), null);
			existingNames.add(normalize(item.getName()));
			existingCodes.add(normalize(item.getCode()));
			created.add(item.getName());
		}
	}

	/** A class is a grade in one academic year at one campus, and sections hang off classes - so without these
	 * the grades alone leave nothing to add a section to. Creates one per CLASS row, per active campus, in
	 * the current academic year, but only for a grade that is itself ticked in the GRADE list - an unselected grade
	 * gets no class and so no sections; never touches other years (use the year rollover for those). Runs right after
	 * applyGrades so grades created in the same apply are found. */
	private void applyClasses(SchoolTemplateResult result, Set<AppModule> disabled) {
		if (disabled.contains(AppModule.ACADEMICS)) {
			return;
		}
		AcademicYear year = academicYearRepository.findByCurrentTrue().orElse(null);
		List<Campus> campuses = activeCampuses();
		if (year == null || campuses.isEmpty()) {
			result.setClassesNote(year == null
					? "No current academic year is set, so no classes were created - mark a year as current, then apply again."
					: "There is no active campus, so no classes were created - add a campus, then apply again.");
			return;
		}
		Map<String, GradeLevel> gradesByName = gradesByName();
		List<SchoolClass> templateClasses = new ArrayList<>();
		for (SchoolTemplateItem item : classRows(disabled)) {
			GradeLevel grade = gradesByName.get(normalize(item.getName()));
			if (grade == null) {
				continue;
			}
			for (Campus campus : campuses) {
				SchoolClass existing = schoolClassRepository
						.findByAcademicYearIdAndCampusIdAndGradeLevelId(year.getId(), campus.getId(), grade.getId()).orElse(null);
				if (existing != null) {
					result.getClassesSkipped().add(classLabel(existing, campuses.size() > 1));
					templateClasses.add(existing);
				} else {
					SchoolClass created = academicService.createSchoolClass(year, campus, grade, null);
					result.getClassesCreated().add(classLabel(created, campuses.size() > 1));
					templateClasses.add(created);
				}
			}
		}
		applySections(result, templateClasses, campuses.size() > 1, disabled);
	}

	/** Every SECTION template row (a fresh catalog has just "A") is added to every class this apply covers, new or
	 * already there, so each class ends up with at least a default section to enrol students into. Skip-if-exists by
	 * name within the class, so a class an admin already sectioned by hand only gains the names it lacks. */
	private void applySections(SchoolTemplateResult result, List<SchoolClass> classes, boolean multiCampus, Set<AppModule> disabled) {
		for (SchoolTemplateItem section : items(SchoolTemplateItemCategory.SECTION, disabled)) {
			for (SchoolClass schoolClass : classes) {
				String label = classLabel(schoolClass, multiCampus) + " - " + section.getName();
				if (sectionRepository.findBySchoolClassIdAndNameIgnoreCase(schoolClass.getId(), section.getName()).isPresent()) {
					result.getSectionsSkipped().add(label);
				} else {
					academicService.createSection(schoolClass, section.getName(), null, null);
					result.getSectionsCreated().add(label);
				}
			}
		}
	}

	private String classLabel(SchoolClass schoolClass, boolean multiCampus) {
		String grade = schoolClass.getGradeLevel().getName();
		return multiCampus ? grade + " (" + schoolClass.getCampus().getName() + ")" : grade;
	}

	/** One entry per SECTION row; "already exists" means every class the template covers in the current year has it
	 * (and at least one class exists - a section with no class to sit in is not done). */
	private List<SchoolTemplateCategoryStatus> sectionStatuses(Set<AppModule> disabled) {
		if (disabled.contains(AppModule.ACADEMICS)) {
			return List.of();
		}
		AcademicYear year = academicYearRepository.findByCurrentTrue().orElse(null);
		List<Campus> campuses = activeCampuses();
		Map<String, GradeLevel> gradesByName = gradesByName();
		List<SchoolClass> classes = new ArrayList<>();
		int expected = 0;
		if (year != null) {
			for (SchoolTemplateItem item : classRows(disabled)) {
				GradeLevel grade = gradesByName.get(normalize(item.getName()));
				if (grade == null) {
					continue;
				}
				for (Campus campus : campuses) {
					expected++;
					schoolClassRepository.findByAcademicYearIdAndCampusIdAndGradeLevelId(year.getId(), campus.getId(), grade.getId())
							.ifPresent(classes::add);
				}
			}
		}
		boolean allClassesExist = expected > 0 && classes.size() == expected;
		return items(SchoolTemplateItemCategory.SECTION, disabled).stream().map(section -> new SchoolTemplateCategoryStatus(section.getName(),
				allClassesExist && classes.stream().allMatch(schoolClass ->
						sectionRepository.findBySchoolClassIdAndNameIgnoreCase(schoolClass.getId(), section.getName()).isPresent()))).toList();
	}

	/** One entry per included grade; "already exists" means every active campus has that grade's class in the current year. */
	private List<SchoolTemplateCategoryStatus> classStatuses(Set<AppModule> disabled) {
		if (disabled.contains(AppModule.ACADEMICS)) {
			return List.of();
		}
		AcademicYear year = academicYearRepository.findByCurrentTrue().orElse(null);
		List<Campus> campuses = activeCampuses();
		Map<String, GradeLevel> gradesByName = gradesByName();
		return classRows(disabled).stream().map(item -> {
			GradeLevel grade = gradesByName.get(normalize(item.getName()));
			boolean exists = year != null && grade != null && !campuses.isEmpty() && campuses.stream().allMatch(campus ->
					schoolClassRepository.findByAcademicYearIdAndCampusIdAndGradeLevelId(year.getId(), campus.getId(), grade.getId()).isPresent());
			return new SchoolTemplateCategoryStatus(item.getName(), exists);
		}).toList();
	}

	/** The ticked CLASS rows whose grade is also a ticked GRADE row - a class never outlives its grade's selection. */
	private List<SchoolTemplateItem> classRows(Set<AppModule> disabled) {
		Set<String> selectedGrades = normalizedNames(items(SchoolTemplateItemCategory.GRADE, disabled).stream().map(SchoolTemplateItem::getName));
		return items(SchoolTemplateItemCategory.CLASS, disabled).stream().filter(row -> selectedGrades.contains(normalize(row.getName()))).toList();
	}

	private List<Campus> activeCampuses() {
		return campusRepository.findAll().stream().filter(campus -> campus.getStatus() == CampusStatus.ACTIVE).toList();
	}

	private Map<String, GradeLevel> gradesByName() {
		Map<String, GradeLevel> byName = new HashMap<>();
		gradeLevelRepository.findAll().forEach(grade -> byName.put(normalize(grade.getName()), grade));
		return byName;
	}

	/** Names and codes only - how many days each type grants is a school policy (and varies by region), so
	 * defaultAnnualDays is left blank for the admin to set in HR. Skips on a name OR code collision, since
	 * leave_types enforces UNIQUE (organisation_id, code). */
	private void applyLeaveTypes(SchoolTemplateResult result, Set<AppModule> disabled) {
		List<LeaveType> existingTypes = leaveTypeRepository.findAll();
		Set<String> existingNames = normalizedNames(existingTypes.stream().map(LeaveType::getName));
		Set<String> existingCodes = normalizedNames(existingTypes.stream().map(LeaveType::getCode));
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.LEAVE_TYPE, disabled)) {
			if (item.getCode() == null || existingNames.contains(normalize(item.getName())) || existingCodes.contains(normalize(item.getCode()))) {
				result.getLeaveTypesSkipped().add(item.getName());
				continue;
			}
			leaveTypeRepository.save(new LeaveType(item.getCode(), item.getName(), null));
			existingNames.add(normalize(item.getName()));
			existingCodes.add(normalize(item.getCode()));
			result.getLeaveTypesCreated().add(item.getName());
		}
	}

	/** Seeds the first grading scale only - GradingScaleController has no default fallback
	 * (unlike ExaminationSettings/DocumentTemplate) and ExaminationService.resolveGrade()
	 * needs an actual persisted scale to produce a letter grade at all. Not a merge: if an
	 * org already has any scale, it's left alone. If every GRADING_BAND item has been
	 * removed from the org's template catalog, there's nothing to seed. */
	private void applyGradingScale(SchoolTemplateResult result, Set<AppModule> disabled) {
		if (!gradingScaleRepository.findAll().isEmpty()) {
			result.setGradingScaleAlreadyExists(true);
			return;
		}
		List<SchoolTemplateItem> bandItems = items(SchoolTemplateItemCategory.GRADING_BAND, disabled);
		if (bandItems.isEmpty()) {
			return;
		}
		List<BandInput> bands = bandItems.stream().map(item -> new BandInput(item.getName(), item.getMinPercentage(), item.getRemark())).toList();
		examinationService.createGradingScale(DEFAULT_GRADING_SCALE_NAME, true, bands);
		result.setGradingScaleCreated(true);
	}

	/** Each row becomes one of the school's certificate types, with the preset wording matching its code (generic wording
	 * when there is none). Skips on a code or name the school already has. */
	private void applyCertificateTypes(SchoolTemplateResult result, Set<AppModule> disabled) {
		for (SchoolTemplateItem item : items(SchoolTemplateItemCategory.CERTIFICATE_TYPE, disabled)) {
			String code = item.getCode() != null && !item.getCode().isBlank() ? item.getCode() : item.getName().replaceAll("[^A-Za-z0-9]+", "_");
			if (certificateTypeService.createFromTemplate(code, item.getName())) {
				result.getCertificateTypesCreated().add(item.getName());
			} else {
				result.getCertificateTypesSkipped().add(item.getName());
			}
		}
	}

	private List<SchoolTemplateCategoryStatus> certificateTypeStatuses(Set<AppModule> disabled) {
		Set<String> existing = normalizedNames(certificateTypeRepository.findAll().stream().map(CertificateType::getName));
		return itemStatuses(SchoolTemplateItemCategory.CERTIFICATE_TYPE, existing, disabled);
	}

	private List<SchoolTemplateItem> items(SchoolTemplateItemCategory category, Set<AppModule> disabled) {
		return schoolTemplateItemService.list(category).stream()
				.filter(SchoolTemplateItem::isIncluded)
				.filter(item -> disabled.isEmpty() || isApplicable(item, disabled))
				.toList();
	}

	/** A fee category needs Fees, an inventory category, item or asset type Inventory, a leave type HR and a grading band Examinations whatever their own tag says. */
	private boolean isApplicable(SchoolTemplateItem item, Set<AppModule> disabled) {
		AppModule implied = switch (item.getCategory()) {
			case FEE_CATEGORY -> AppModule.FEES;
			case LEAVE_TYPE -> AppModule.HR;
			case SECTION, CLASS, SUBJECT -> AppModule.ACADEMICS;
			case ITEM_CATEGORY, ITEM, ASSET_TYPE -> AppModule.INVENTORY;
			case GRADING_BAND -> AppModule.EXAMINATIONS;
			case CERTIFICATE_TYPE -> AppModule.STUDENTS;
			default -> null;
		};
		if (implied != null && disabled.contains(implied)) {
			return false;
		}
		return item.getModuleCode() == null || AppModule.fromCode(item.getModuleCode()).map(module -> !disabled.contains(module)).orElse(true);
	}

	private List<SchoolTemplateCategoryStatus> itemStatuses(SchoolTemplateItemCategory category, Set<String> existingNormalizedNames,
			Set<AppModule> disabled) {
		return items(category, disabled).stream()
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
