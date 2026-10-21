package com.operion.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.academic.AcademicService;
import com.operion.certificate.CertificateTypePresetRepository;
import com.operion.certificate.CertificateTypeRepository;
import com.operion.certificate.CertificateTypeService;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import com.operion.academic.SubjectRepository;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.authorization.RoleService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationSettingsRepository;
import com.operion.examination.GradingScaleBandRepository;
import com.operion.examination.GradingScaleRepository;
import com.operion.examination.MarksEntryRegisterRepository;
import com.operion.examination.MarksEntryRepository;
import com.operion.examination.ReportCardRepository;
import com.operion.finance.AdjustmentRepository;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeDocumentCounterRepository;
import com.operion.finance.FeeService;
import com.operion.finance.FeeStructureGroupRepository;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.PaymentAllocationRepository;
import com.operion.finance.PaymentRepository;
import com.operion.finance.RefundRepository;
import com.operion.finance.StudentFeeAssignmentRepository;
import com.operion.finance.WaiverRepository;
import com.operion.inventory.InventoryService;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.modules.AppModule;
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockAdjustmentRepository;
import com.operion.inventory.StockEntryRepository;
import com.operion.inventory.StockIssueRepository;
import com.operion.inventory.AssetRepository;
import com.operion.inventory.StockTransferRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SchoolTemplateServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private CertificateTypeRepository certificateTypeRepository;
	@Autowired
	private CertificateTypePresetRepository certificateTypePresetRepository;
	@Autowired
	private GradeLevelRepository gradeLevelRepository;
	@Autowired
	private SubjectRepository subjectRepository;
	@Autowired
	private SchoolClassRepository schoolClassRepository;
	@Autowired
	private SectionRepository sectionRepository;
	@Autowired
	private ClassSubjectRepository classSubjectRepository;
	@Autowired
	private TeacherAssignmentRepository teacherAssignmentRepository;
	@Autowired
	private DepartmentRepository departmentRepository;
	@Autowired
	private DesignationRepository designationRepository;
	@Autowired
	private RoleRepository roleRepository;
	@Autowired
	private PermissionRepository permissionRepository;
	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private com.operion.authorization.OrganisationMembershipRepository membershipRepository;
	@Autowired
	private FeeCategoryRepository feeCategoryRepository;
	@Autowired
	private FeeStructureGroupRepository feeStructureGroupRepository;
	@Autowired
	private FeeStructureRepository feeStructureRepository;
	@Autowired
	private FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	@Autowired
	private StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	@Autowired
	private InvoiceRepository invoiceRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private PaymentAllocationRepository paymentAllocationRepository;
	@Autowired
	private RefundRepository refundRepository;
	@Autowired
	private AdjustmentRepository adjustmentRepository;
	@Autowired
	private WaiverRepository waiverRepository;
	@Autowired
	private FeeDocumentCounterRepository feeDocumentCounterRepository;
	@Autowired
	private OrganisationBrandingRepository organisationBrandingRepository;
	@Autowired
	private ItemCategoryRepository itemCategoryRepository;
	@Autowired
	private ItemRepository itemRepository;
	@Autowired
	private com.operion.hr.LeaveTypeRepository leaveTypeRepository;
	@Autowired
	private com.operion.organisation.AcademicYearRepository academicYearRepository;
	@Autowired
	private com.operion.organisation.CampusRepository campusRepository;
	@Autowired
	private StockEntryRepository stockEntryRepository;
	@Autowired
	private StockIssueRepository stockIssueRepository;
	@Autowired
	private StockAdjustmentRepository stockAdjustmentRepository;
	@Autowired
	private StockTransferRepository stockTransferRepository;
	@Autowired
	private AssetRepository assetRepository;
	@Autowired
	private ExamRepository examRepository;
	@Autowired
	private ExamScheduleRepository examScheduleRepository;
	@Autowired
	private GradingScaleRepository gradingScaleRepository;
	@Autowired
	private GradingScaleBandRepository gradingScaleBandRepository;
	@Autowired
	private MarksEntryRepository marksEntryRepository;
	@Autowired
	private MarksEntryRegisterRepository marksEntryRegisterRepository;
	@Autowired
	private ReportCardRepository reportCardRepository;
	@Autowired
	private ExaminationSettingsRepository examinationSettingsRepository;
	@Autowired
	private SchoolTemplateItemRepository schoolTemplateItemRepository;
	@Autowired
	private SchoolTemplateMasterItemRepository schoolTemplateMasterItemRepository;

	private SchoolTemplateService schoolTemplateService;
	private SchoolTemplateItemService schoolTemplateItemService;

	@BeforeEach
	void setUp() {
		seedMasterCatalogOnce();


		AuditLogService auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
		AcademicService academicService = new AcademicService(gradeLevelRepository, subjectRepository, schoolClassRepository,
				sectionRepository, classSubjectRepository, teacherAssignmentRepository);
		RoleService roleService = new RoleService(roleRepository, permissionRepository, auditLogService, membershipRepository);
		FeeService feeService = new FeeService(feeCategoryRepository, feeStructureGroupRepository, feeStructureRepository,
				feeStructureInstallmentRepository, studentFeeAssignmentRepository, invoiceRepository, paymentRepository,
				paymentAllocationRepository, refundRepository, adjustmentRepository, waiverRepository, feeDocumentCounterRepository,
				organisationBrandingRepository, new org.springframework.beans.factory.support.StaticListableBeanFactory(
						java.util.Map.of("auditLogService", auditLogService)).getBeanProvider(com.operion.audit.AuditLogService.class));
		InventoryService inventoryService = new InventoryService(itemCategoryRepository, itemRepository, stockEntryRepository,
				stockIssueRepository, stockAdjustmentRepository, stockTransferRepository, assetRepository);
		ExaminationService examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, marksEntryRegisterRepository, reportCardRepository,
				examinationSettingsRepository, auditLogService);
		schoolTemplateItemService = new SchoolTemplateItemService(schoolTemplateItemRepository, schoolTemplateMasterItemRepository);

		schoolTemplateService = new SchoolTemplateService(schoolTemplateItemService, academicService, gradeLevelRepository,
				departmentRepository, designationRepository, roleRepository, roleService, permissionRepository, feeService,
				feeCategoryRepository, inventoryService, itemCategoryRepository, itemRepository, leaveTypeRepository, examinationService, gradingScaleRepository,
				academicYearRepository, campusRepository, schoolClassRepository, sectionRepository, subjectRepository,
				new CertificateTypeService(certificateTypeRepository, certificateTypePresetRepository), certificateTypeRepository);

		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "school-template-test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	/** V100's seed data isn't exercised under the H2 test profile (Flyway disabled, see
	 * src/test/resources/application.properties) - this stands in for it. Guarded by an
	 * emptiness check since @Transactional(NOT_SUPPORTED) means nothing rolls back between
	 * test methods in this class (school_template_master_items is global, not per-org, so
	 * a per-test insert would otherwise accumulate duplicates across the whole class run). */
	private void seedMasterCatalogOnce() {
		if (!schoolTemplateMasterItemRepository.findAll().isEmpty()) {
			return;
		}
		String[] grades = { "LKG", "UKG", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9",
				"Grade 10" };
		for (int i = 0; i < grades.length; i++) {
			String stage = i < 2 ? "Pre-Primary" : i < 7 ? "Primary" : "Secondary";
			schoolTemplateMasterItemRepository.save(
					new SchoolTemplateMasterItem(SchoolTemplateItemCategory.GRADE, grades[i], null, null, i, stage, null, null, null, null));
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
					SchoolTemplateItemCategory.CLASS, grades[i], null, null, i, stage, null, null, null, null).forModule("ACADEMICS"));
		}
		for (String name : new String[] { "Administration", "Academics", "Finance & Accounts", "HR", "Operations", "Transport", "Library",
				"IT", "Examination", "Admissions" }) {
			schoolTemplateMasterItemRepository.save(
					new SchoolTemplateMasterItem(SchoolTemplateItemCategory.DEPARTMENT, name, null, null, null, null, null, null, null, null));
		}
		for (String name : new String[] { "Principal", "Vice Principal", "HOD", "Academic Coordinator", "Teacher", "Assistant Teacher",
				"Accountant", "HR Manager", "Admin Officer", "Receptionist", "Librarian", "Transport Manager", "Driver", "Storekeeper",
				"Purchase Manager", "IT Admin", "Support Staff" }) {
			schoolTemplateMasterItemRepository.save(
					new SchoolTemplateMasterItem(SchoolTemplateItemCategory.DESIGNATION, name, null, null, null, null, null, null, null, null));
		}
		for (String name : new String[] { "Administrator", "Principal", "Academic Admin", "Teacher", "Accountant", "HR Manager", "Librarian",
				"Transport Manager", "Inventory Manager", "Purchase Manager", "Receptionist", "Staff" }) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
					SchoolTemplateItemCategory.ROLE, name, null, "Fixture role", null, null, null, null, null, "STUDENT_VIEW"));
		}
		String[][] feeCategories = { { "TUITION", "Tuition Fee", "GENERAL" }, { "ADMISSION", "Admission Fee", "GENERAL" },
				{ "EXAMINATION", "Examination Fee", "GENERAL" }, { "TRANSPORT", "Transport Fee", "TRANSPORT" },
				{ "LIBRARY", "Library Fee", "GENERAL" }, { "OTHER", "Other Fee", "GENERAL" } };
		for (String[] fee : feeCategories) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
					SchoolTemplateItemCategory.FEE_CATEGORY, fee[1], fee[0], null, null, null, fee[2], null, null, null));
		}
		String[][] itemCategories = { { "STATIONERY", "Stationery" }, { "FURNITURE", "Furniture" }, { "ELECTRONICS", "Electronics" },
				{ "CLEANING_SUPPLIES", "Cleaning Supplies" }, { "SPORTS", "Sports" }, { "LABORATORY", "Laboratory" }, { "GENERAL", "General" } };
		for (String[] item : itemCategories) {
			schoolTemplateMasterItemRepository.save(
					new SchoolTemplateMasterItem(SchoolTemplateItemCategory.ITEM_CATEGORY, item[1], item[0], null, null, null, null, null, null, null));
		}
		Object[][] inventoryItems = { { SchoolTemplateItemCategory.ITEM, "Notebook", "NOTEBOOK", "PCS", "STATIONERY" },
				{ SchoolTemplateItemCategory.ITEM, "Floor Cleaner", "FLOOR_CLEANER", "LTR", "CLEANING_SUPPLIES" },
				{ SchoolTemplateItemCategory.ITEM, "Orphan Item", "ORPHAN_ITEM", "PCS", "NO_SUCH_CATEGORY" },
				{ SchoolTemplateItemCategory.ASSET_TYPE, "Projector", "PROJECTOR", "PCS", "ELECTRONICS" },
				{ SchoolTemplateItemCategory.ASSET_TYPE, "Student Desk", "DESK_STUDENT", "PCS", "FURNITURE" } };
		for (Object[] row : inventoryItems) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem((SchoolTemplateItemCategory) row[0], (String) row[1],
					(String) row[2], null, null, null, null, null, null, null).withItemDetails((String) row[3], (String) row[4]));
		}
		schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
				SchoolTemplateItemCategory.SECTION, "A", null, null, null, null, null, null, null, null).forModule("ACADEMICS"));
		String[][] subjects = { { "English", "ENG" }, { "Mathematics", "MATH" } };
		for (String[] subject : subjects) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
					SchoolTemplateItemCategory.SUBJECT, subject[0], subject[1], null, null, null, null, null, null, null).forModule("ACADEMICS"));
		}
		String[][] leaveTypes = { { "Casual Leave", "CASUAL" }, { "Sick Leave", "SICK" }, { "Unpaid Leave", "UNPAID" } };
		for (String[] leave : leaveTypes) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
					SchoolTemplateItemCategory.LEAVE_TYPE, leave[0], leave[1], null, null, null, null, null, null, null).forModule("HR"));
		}
		Object[][] bands = { { "A+", 0, 90.0 }, { "A", 1, 80.0 }, { "B+", 2, 70.0 }, { "B", 3, 60.0 }, { "C+", 4, 50.0 }, { "C", 5, 40.0 },
				{ "D", 6, 33.0 }, { "F", 7, 0.0 } };
		for (Object[] band : bands) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(SchoolTemplateItemCategory.GRADING_BAND, (String) band[0],
					null, null, (Integer) band[1], null, null, (Double) band[2], null, null));
		}
		// Stand-in for V28's certificate presets and template rows: one with a preset, one custom without.
		certificateTypePresetRepository.save(new com.operion.certificate.CertificateTypePreset("TC", "Transfer Certificate", "TRANSFER CERTIFICATE",
				"Certified for {{student.name}}.", null, null, "TC-{YYYY}-{SEQ:4}", true, true, true, true, false));
		schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
				SchoolTemplateItemCategory.CERTIFICATE_TYPE, "Transfer Certificate", "TC", null, null, null, null, null, null, null).forModule("STUDENTS"));
		schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(
				SchoolTemplateItemCategory.CERTIFICATE_TYPE, "Sports Participation", "SPORTS", null, null, null, null, null, null, null).forModule("STUDENTS"));
		// Stand-in for V132's module tagging.
		java.util.Map<String, String> tags = java.util.Map.of("DEPARTMENT:Transport", "TRANSPORT", "DESIGNATION:Driver", "TRANSPORT",
				"DESIGNATION:Transport Manager", "TRANSPORT", "ROLE:Transport Manager", "TRANSPORT", "FEE_CATEGORY:Transport Fee",
				"TRANSPORT", "DEPARTMENT:Library", "LIBRARY", "ROLE:Librarian", "LIBRARY");
		schoolTemplateMasterItemRepository.saveAll(schoolTemplateMasterItemRepository.findAll().stream()
				.filter(item -> tags.containsKey(item.getCategory() + ":" + item.getName()))
				.map(item -> item.forModule(tags.get(item.getCategory() + ":" + item.getName()))).toList());
	}

	@Test
	void certificateTypesAreCopiedFromTheirPresetOrGetGenericWordingAndAreNotDuplicatedOnReapply() {
		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getCertificateTypesCreated()).containsExactlyInAnyOrder("Transfer Certificate", "Sports Participation");
		com.operion.certificate.CertificateType tc = certificateTypeRepository.findByCode("TC").orElseThrow();
		assertThat(tc.isApprovalRequired()).isTrue();
		assertThat(tc.isRequiresExit()).isTrue();
		assertThat(tc.getBodyTemplate()).isEqualTo("Certified for {{student.name}}.");
		com.operion.certificate.CertificateType sports = certificateTypeRepository.findByCode("SPORTS").orElseThrow();
		assertThat(sports.isApprovalRequired()).isFalse();
		assertThat(sports.getBodyTemplate()).contains("{{student.name}}");
		assertThat(sports.getNumberFormat()).isEqualTo("SPORTS-{YYYY}-{SEQ:4}");
		assertThat(schoolTemplateService.preview().certificateTypes()).allMatch(SchoolTemplateCategoryStatus::alreadyExists);

		SchoolTemplateResult again = schoolTemplateService.apply();
		assertThat(again.getCertificateTypesCreated()).isEmpty();
		assertThat(again.getCertificateTypesSkipped()).hasSize(2);
	}

	@Test
	void certificateTypesFollowTheStudentsModule() {
		SchoolTemplateResult result = schoolTemplateService.apply(java.util.Set.of(AppModule.STUDENTS));

		assertThat(result.getCertificateTypesCreated()).isEmpty();
		assertThat(certificateTypeRepository.findAll()).isEmpty();
	}

	@Test
	void itemsOfADisabledModuleAreNotCreated() {
		SchoolTemplateResult result = schoolTemplateService.apply(java.util.Set.of(AppModule.TRANSPORT));

		assertThat(result.getDepartmentsCreated()).hasSize(9).doesNotContain("Transport");
		assertThat(result.getDesignationsCreated()).hasSize(15).doesNotContain("Driver", "Transport Manager");
		assertThat(result.getRolesCreated()).hasSize(11).doesNotContain("Transport Manager");
		assertThat(result.getFeeCategoriesCreated()).hasSize(5).doesNotContain("Transport Fee");
		// Grades, item categories and the grading scale are not tied to Transport.
		assertThat(result.getGradesCreated()).hasSize(12);
		assertThat(result.getItemCategoriesCreated()).hasSize(7);
		assertThat(result.isGradingScaleCreated()).isTrue();
	}

	@Test
	void categoriesImpliedByAModuleFollowItEvenWithoutATag() {
		SchoolTemplateResult result = schoolTemplateService.apply(java.util.Set.of(AppModule.FEES, AppModule.INVENTORY, AppModule.EXAMINATIONS));

		assertThat(result.getFeeCategoriesCreated()).isEmpty();
		assertThat(result.getItemCategoriesCreated()).isEmpty();
		assertThat(result.getItemsCreated()).isEmpty();
		assertThat(result.getAssetTypesCreated()).isEmpty();
		assertThat(result.isGradingScaleCreated()).isFalse();
		assertThat(gradingScaleRepository.findAll()).isEmpty();
	}

	@Test
	void itemsAndAssetTypesBecomeCatalogItemsInTheirCategoryWithoutAnyStockOrAssetUnits() {
		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getItemsCreated()).containsExactlyInAnyOrder("Notebook", "Floor Cleaner");
		assertThat(result.getAssetTypesCreated()).containsExactlyInAnyOrder("Projector", "Student Desk");
		var floorCleaner = itemRepository.findAll().stream().filter(item -> item.getName().equals("Floor Cleaner")).findFirst().orElseThrow();
		assertThat(floorCleaner.getCode()).isEqualTo("FLOOR_CLEANER");
		assertThat(floorCleaner.getUnit()).isEqualTo("LTR");
		assertThat(floorCleaner.getCategory().getCode()).isEqualTo("CLEANING_SUPPLIES");
		assertThat(stockEntryRepository.findAll()).isEmpty();
		assertThat(assetRepository.findAll()).isEmpty();
	}

	@Test
	void anItemWhoseCategoryTheOrganisationDoesNotHaveIsSkippedNotInvented() {
		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getItemsSkipped()).containsExactly("Orphan Item");
		assertThat(itemRepository.findAll()).extracting(com.operion.inventory.Item::getName).doesNotContain("Orphan Item");
		assertThat(itemCategoryRepository.findAll()).hasSize(7);
	}

	@Test
	void anExistingItemWithTheSameNameOrCodeIsLeftAlone() {
		var stationery = itemCategoryRepository.save(new com.operion.inventory.ItemCategory("STATIONERY", "Stationery", null));
		itemRepository.save(new com.operion.inventory.Item(stationery, "NB-1", "notebook", "BOX", null));
		itemRepository.save(new com.operion.inventory.Item(stationery, "PROJECTOR", "Ceiling Projector", "PCS", null));

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getItemsSkipped()).contains("Notebook");
		assertThat(result.getAssetTypesSkipped()).contains("Projector");
		assertThat(itemRepository.findAll().stream().filter(item -> item.getName().equalsIgnoreCase("notebook"))).hasSize(1);
	}

	@Test
	void rowsMarkedNotRequiredAreKeptButNeitherPreviewedNorApplied() {
		var notebook = schoolTemplateItemService.list(SchoolTemplateItemCategory.ITEM).stream()
				.filter(item -> item.getName().equals("Notebook")).findFirst().orElseThrow();
		var teacher = schoolTemplateItemService.list(SchoolTemplateItemCategory.DESIGNATION).stream()
				.filter(item -> item.getName().equals("Driver")).findFirst().orElseThrow();
		schoolTemplateItemService.setIncluded(notebook.getId(), false);
		schoolTemplateItemService.setIncluded(teacher.getId(), false);

		assertThat(schoolTemplateService.preview().items()).extracting(SchoolTemplateCategoryStatus::name).doesNotContain("Notebook");
		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getItemsCreated()).containsExactly("Floor Cleaner");
		assertThat(result.getDesignationsCreated()).hasSize(16).doesNotContain("Driver");
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.ITEM)).extracting(SchoolTemplateItem::getName).contains("Notebook");

		schoolTemplateItemService.setIncluded(notebook.getId(), true);
		assertThat(schoolTemplateService.apply().getItemsCreated()).containsExactly("Notebook");
	}

	@Test
	void selectNoneForACategorySkipsItEntirelyAndSelectAllRestoresIt() {
		schoolTemplateItemService.setIncludedForCategory(SchoolTemplateItemCategory.ASSET_TYPE, false);
		assertThat(schoolTemplateService.apply().getAssetTypesCreated()).isEmpty();

		schoolTemplateItemService.setIncludedForCategory(SchoolTemplateItemCategory.ASSET_TYPE, true);
		assertThat(schoolTemplateService.apply().getAssetTypesCreated()).containsExactlyInAnyOrder("Projector", "Student Desk");
	}

	@Test
	void leaveTypesAreCreatedWithoutDefaultDaysAndSkippedOnANameOrCodeClash() {
		leaveTypeRepository.save(new com.operion.hr.LeaveType("SICK", "Medical Leave", 10.0));

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getLeaveTypesCreated()).containsExactlyInAnyOrder("Casual Leave", "Unpaid Leave");
		assertThat(result.getLeaveTypesSkipped()).containsExactly("Sick Leave");
		assertThat(leaveTypeRepository.findAll()).extracting(com.operion.hr.LeaveType::getCode).containsExactlyInAnyOrder("SICK", "CASUAL", "UNPAID");
		assertThat(leaveTypeRepository.findAll().stream().filter(type -> type.getCode().equals("CASUAL")).findFirst().orElseThrow().getDefaultAnnualDays()).isNull();
		assertThat(schoolTemplateService.apply().getLeaveTypesCreated()).isEmpty();
	}

	@Test
	void leaveTypesFollowTheHrModule() {
		assertThat(schoolTemplateService.apply(java.util.Set.of(AppModule.HR)).getLeaveTypesCreated()).isEmpty();
		assertThat(schoolTemplateService.preview(java.util.Set.of(AppModule.HR)).leaveTypes()).isEmpty();
	}

	private com.operion.organisation.AcademicYear currentYear() {
		var year = new com.operion.organisation.AcademicYear("2026-27", java.time.LocalDate.of(2026, 6, 1), java.time.LocalDate.of(2027, 4, 30));
		year.markCurrent();
		return academicYearRepository.save(year);
	}

	@Test
	void classesAreCreatedPerIncludedGradePerActiveCampusInTheCurrentYearOnly() {
		var year = currentYear();
		campusRepository.save(new com.operion.organisation.Campus("Main Campus", "MAIN"));
		campusRepository.save(new com.operion.organisation.Campus("North Campus", "NORTH"));
		var lkg = schoolTemplateItemService.list(SchoolTemplateItemCategory.GRADE).stream().filter(item -> item.getName().equals("LKG")).findFirst().orElseThrow();
		schoolTemplateItemService.setIncluded(lkg.getId(), false);

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getClassesNote()).isNull();
		assertThat(result.getClassesCreated()).hasSize(22).contains("Grade 5 (Main Campus)", "Grade 5 (North Campus)")
				.noneMatch(label -> label.startsWith("LKG"));
		assertThat(schoolClassRepository.findByAcademicYearId(year.getId())).hasSize(22);
		assertThat(schoolTemplateService.preview().classes()).allMatch(SchoolTemplateCategoryStatus::alreadyExists);

		SchoolTemplateResult second = schoolTemplateService.apply();
		assertThat(second.getClassesCreated()).isEmpty();
		assertThat(second.getClassesSkipped()).hasSize(22);
	}

	@Test
	void aClassIsOnlyCreatedWhenItsGradeIsTickedToo() {
		var year = currentYear();
		campusRepository.save(new com.operion.organisation.Campus("Main Campus", "MAIN"));
		var grade6 = schoolTemplateItemService.list(SchoolTemplateItemCategory.GRADE).stream().filter(item -> item.getName().equals("Grade 6")).findFirst().orElseThrow();
		var class7 = schoolTemplateItemService.list(SchoolTemplateItemCategory.CLASS).stream().filter(item -> item.getName().equals("Grade 7")).findFirst().orElseThrow();
		schoolTemplateItemService.setIncluded(grade6.getId(), false);
		schoolTemplateItemService.setIncluded(class7.getId(), false);

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getGradesCreated()).doesNotContain("Grade 6").contains("Grade 7");
		assertThat(result.getClassesCreated()).hasSize(10).doesNotContain("Grade 6", "Grade 7");
		assertThat(result.getSectionsCreated()).hasSize(10).noneMatch(label -> label.startsWith("Grade 6") || label.startsWith("Grade 7"));
		assertThat(schoolClassRepository.findByAcademicYearId(year.getId())).hasSize(10);
		assertThat(schoolTemplateService.preview().classes()).extracting(SchoolTemplateCategoryStatus::name).doesNotContain("Grade 6", "Grade 7");
	}

	@Test
	void classRowsFollowGradeRowsAddedRenamedAndRemovedInTheCatalog() {
		schoolTemplateItemService.list(SchoolTemplateItemCategory.GRADE);
		var grade11 = schoolTemplateItemService.create(SchoolTemplateItemCategory.GRADE, "Grade 11", null, null, 12, "Senior", null, null, null,
				null, null, null);
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.CLASS)).extracting(SchoolTemplateItem::getName).contains("Grade 11");

		schoolTemplateItemService.update(grade11.getId(), "Grade XI", null, null, 12, "Senior", null, null, null, null, null, null);
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.CLASS)).extracting(SchoolTemplateItem::getName)
				.contains("Grade XI").doesNotContain("Grade 11");

		schoolTemplateItemService.delete(grade11.getId());
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.CLASS)).extracting(SchoolTemplateItem::getName).doesNotContain("Grade XI");
	}

	@Test
	void subjectsAreCreatedOnceAndAHandMadeSubjectIsSkipped() {
		subjectRepository.save(new com.operion.academic.Subject("english", null));
		schoolTemplateItemService.list(SchoolTemplateItemCategory.SUBJECT);

		SchoolTemplateResult first = schoolTemplateService.apply();

		assertThat(first.getSubjectsCreated()).containsExactly("Mathematics");
		assertThat(first.getSubjectsSkipped()).containsExactly("English");
		assertThat(schoolTemplateService.preview().subjects()).allMatch(SchoolTemplateCategoryStatus::alreadyExists);

		SchoolTemplateResult second = schoolTemplateService.apply();
		assertThat(second.getSubjectsCreated()).isEmpty();
		assertThat(subjectRepository.findAll()).hasSize(2);
	}

	@Test
	void everyClassGetsEachSectionRowAndRepeatsOrHandMadeSectionsAreNotDuplicated() {
		var year = currentYear();
		var campus = campusRepository.save(new com.operion.organisation.Campus("Main Campus", "MAIN"));
		schoolTemplateItemService.list(SchoolTemplateItemCategory.SECTION); // loading the catalog copies the defaults in, as the settings screen does first
		schoolTemplateItemService.create(SchoolTemplateItemCategory.SECTION, "B", null, null, null, null, null, null, null, null, null, null);

		SchoolTemplateResult first = schoolTemplateService.apply();

		assertThat(first.getSectionsCreated()).hasSize(24).contains("Grade 5 - A", "Grade 5 - B");
		assertThat(sectionRepository.findAll()).hasSize(24);
		assertThat(sectionRepository.findAll()).allMatch(section -> section.getCapacity() == null && section.getRoom() == null);
		assertThat(schoolTemplateService.preview().sections()).allMatch(SchoolTemplateCategoryStatus::alreadyExists);

		SchoolTemplateResult second = schoolTemplateService.apply();
		assertThat(second.getSectionsCreated()).isEmpty();
		assertThat(second.getSectionsSkipped()).hasSize(24);
		assertThat(schoolClassRepository.findByAcademicYearId(year.getId())).hasSize(12);
		assertThat(campus.getId()).isNotNull();
	}

	@Test
	void anUntickedSectionRowIsNotAddedAndTheStatusShowsItAsPending() {
		currentYear();
		campusRepository.save(new com.operion.organisation.Campus("Main Campus", "MAIN"));
		var a = schoolTemplateItemService.list(SchoolTemplateItemCategory.SECTION).stream().filter(item -> item.getName().equals("A")).findFirst().orElseThrow();
		schoolTemplateItemService.setIncluded(a.getId(), false);

		assertThat(schoolTemplateService.apply().getSectionsCreated()).isEmpty();
		assertThat(sectionRepository.findAll()).isEmpty();

		schoolTemplateItemService.setIncluded(a.getId(), true);
		assertThat(schoolTemplateService.preview().sections()).noneMatch(SchoolTemplateCategoryStatus::alreadyExists);
		assertThat(schoolTemplateService.apply().getSectionsCreated()).hasSize(12);
	}

	@Test
	void withoutACurrentYearOrCampusNoClassIsCreatedAndTheReasonIsReported() {
		assertThat(schoolTemplateService.apply().getClassesNote()).contains("No current academic year");
		currentYear();
		assertThat(schoolTemplateService.apply().getClassesNote()).contains("no active campus");
		assertThat(schoolClassRepository.findAll()).isEmpty();
	}

	@Test
	void classesFollowTheAcademicsModule() {
		currentYear();
		campusRepository.save(new com.operion.organisation.Campus("Main Campus", "MAIN"));

		assertThat(schoolTemplateService.apply(java.util.Set.of(AppModule.ACADEMICS)).getClassesCreated()).isEmpty();
		assertThat(schoolTemplateService.preview(java.util.Set.of(AppModule.ACADEMICS)).classes()).isEmpty();
	}

	@Test
	void previewReportsWhichItemsAndAssetTypesAlreadyExist() {
		schoolTemplateService.apply();

		SchoolTemplateStatus status = schoolTemplateService.preview();

		assertThat(status.items()).extracting(SchoolTemplateCategoryStatus::name).contains("Notebook", "Floor Cleaner");
		assertThat(status.assetTypes()).extracting(SchoolTemplateCategoryStatus::name).containsExactlyInAnyOrder("Projector", "Student Desk");
		assertThat(status.assetTypes()).allMatch(SchoolTemplateCategoryStatus::alreadyExists);
	}

	@Test
	void previewHidesItemsOfADisabledModuleToo() {
		SchoolTemplateStatus status = schoolTemplateService.preview(java.util.Set.of(AppModule.TRANSPORT, AppModule.LIBRARY));

		assertThat(status.departments()).hasSize(8).extracting(SchoolTemplateCategoryStatus::name).doesNotContain("Transport", "Library");
		assertThat(status.feeCategories()).hasSize(5);
	}

	@Test
	void appliesEveryDefaultToAFreshOrganisation() {
		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getGradesCreated()).hasSize(12);
		assertThat(result.getDepartmentsCreated()).hasSize(10);
		assertThat(result.getDesignationsCreated()).hasSize(17);
		assertThat(result.getRolesCreated()).hasSize(12);
		assertThat(result.getFeeCategoriesCreated()).hasSize(6);
		assertThat(result.getItemCategoriesCreated()).hasSize(7);
		assertThat(result.isGradingScaleCreated()).isTrue();

		assertThat(gradeLevelRepository.findAll()).hasSize(12);
		assertThat(departmentRepository.findAll()).hasSize(10);
		assertThat(designationRepository.findAll()).hasSize(17);
		assertThat(feeCategoryRepository.findAll()).hasSize(6);
		assertThat(itemCategoryRepository.findAll()).hasSize(7);
		assertThat(gradingScaleRepository.findAll()).hasSize(1);
	}

	@Test
	void reapplyingIsANoOpEverywhere() {
		schoolTemplateService.apply();

		SchoolTemplateResult second = schoolTemplateService.apply();

		assertThat(second.getGradesCreated()).isEmpty();
		assertThat(second.getDepartmentsCreated()).isEmpty();
		assertThat(second.getDesignationsCreated()).isEmpty();
		assertThat(second.getRolesCreated()).isEmpty();
		assertThat(second.getFeeCategoriesCreated()).isEmpty();
		assertThat(second.getItemCategoriesCreated()).isEmpty();
		assertThat(second.getItemsCreated()).isEmpty();
		assertThat(second.getAssetTypesCreated()).isEmpty();
		assertThat(second.isGradingScaleCreated()).isFalse();
		assertThat(second.isGradingScaleAlreadyExists()).isTrue();

		assertThat(second.getGradesSkipped()).hasSize(12);
		assertThat(second.getDepartmentsSkipped()).hasSize(10);
		assertThat(second.getDesignationsSkipped()).hasSize(17);
		assertThat(second.getRolesSkipped()).hasSize(12);
		assertThat(second.getFeeCategoriesSkipped()).hasSize(6);
		assertThat(second.getItemCategoriesSkipped()).hasSize(7);

		assertThat(gradeLevelRepository.findAll()).hasSize(12);
		assertThat(departmentRepository.findAll()).hasSize(10);
		assertThat(gradingScaleRepository.findAll()).hasSize(1);
	}

	@Test
	void doesNotDuplicateAPreExistingCaseInsensitiveMatch() {
		departmentRepository.save(new Department("administration"));
		designationRepository.save(new Designation("TEACHER"));

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getDepartmentsCreated()).doesNotContain("Administration").hasSize(9);
		assertThat(result.getDepartmentsSkipped()).contains("Administration");
		assertThat(result.getDesignationsCreated()).doesNotContain("Teacher").hasSize(16);
		assertThat(result.getDesignationsSkipped()).contains("Teacher");

		assertThat(departmentRepository.findAll()).hasSize(10);
		assertThat(designationRepository.findAll()).hasSize(17);
	}

	@Test
	void organisationOwnerAliasesTheExistingOwnerRoleInsteadOfDuplicatingIt() {
		roleRepository.save(new Role("Owner", "Full access - system default, cannot be locked out", true));

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getRolesCreated()).doesNotContain("Organisation Owner");
		assertThat(result.getRolesSkipped()).contains("Organisation Owner");
		assertThat(roleRepository.findByName("Organisation Owner")).isEmpty();
		assertThat(roleRepository.findAll()).hasSize(13);
	}

	@Test
	void gradingScaleIsSeededOnlyOnce() {
		schoolTemplateService.apply();
		SchoolTemplateResult second = schoolTemplateService.apply();

		assertThat(second.isGradingScaleCreated()).isFalse();
		assertThat(gradingScaleRepository.findAll()).hasSize(1);
	}

	/** Regression for the real bug this catalog reported in production: an org that
	 * manually created "Grade 5" at sequence_order=5 (a plausible value, since the org
	 * just picked "the fifth grade") used to collide with the template's own hardcoded
	 * sequence_order=5 for "Grade 4", tripping grade_levels' UNIQUE (organisation_id,
	 * sequence_order) constraint and failing the whole apply(). Sequence order must now
	 * always be computed as (current max + 1), never trusted from the template item. */
	@Test
	void gradeSequenceOrderNeverCollidesWithAnExistingManuallyCreatedGrade() {
		gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "Primary"));

		SchoolTemplateResult result = schoolTemplateService.apply();

		assertThat(result.getGradesSkipped()).contains("Grade 5");
		assertThat(result.getGradesCreated()).hasSize(11).contains("Grade 4", "Grade 6");

		List<GradeLevel> grades = gradeLevelRepository.findAll();
		assertThat(grades).hasSize(12);
		assertThat(grades.stream().map(GradeLevel::getSequenceOrder).distinct()).hasSize(12);
	}

	@Test
	void templateItemsCanBeAddedEditedAndRemovedWithoutAnyCodeChange() {
		var created = schoolTemplateItemService.create(SchoolTemplateItemCategory.DEPARTMENT, "Sports", null, null, null, null, null, null,
				null, null, null, null);
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.DEPARTMENT))
				.extracting(SchoolTemplateItem::getName)
				.contains("Sports");

		schoolTemplateItemService.update(created.getId(), "Sports & Athletics", null, null, null, null, null, null, null, null, null,
				null);
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.DEPARTMENT))
				.extracting(SchoolTemplateItem::getName)
				.contains("Sports & Athletics")
				.doesNotContain("Sports");

		schoolTemplateItemService.delete(created.getId());
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.DEPARTMENT))
				.extracting(SchoolTemplateItem::getName)
				.doesNotContain("Sports & Athletics");
	}

	@Test
	void aFreshOrganisationIsBootstrappedExactlyOnce() {
		assertThat(schoolTemplateItemRepository.findAll()).isEmpty();

		schoolTemplateItemService.ensureSeeded();
		int seededCount = schoolTemplateItemRepository.findAll().size();
		assertThat(seededCount).isGreaterThan(0);

		// A grade row would take its matching class row with it, so remove a row with no counterpart.
		schoolTemplateItemService.delete(schoolTemplateItemRepository.findAll().stream()
				.filter(row -> row.getCategory() == SchoolTemplateItemCategory.DEPARTMENT).findFirst().orElseThrow().getId());
		schoolTemplateItemService.ensureSeeded();

		assertThat(schoolTemplateItemRepository.findAll()).hasSize(seededCount - 1);
	}
}
