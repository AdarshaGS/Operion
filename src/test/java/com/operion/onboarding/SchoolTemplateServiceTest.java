package com.operion.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.academic.AcademicService;
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
import com.operion.inventory.ItemRepository;
import com.operion.inventory.StockAdjustmentRepository;
import com.operion.inventory.StockEntryRepository;
import com.operion.inventory.StockIssueRepository;
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
	private StockEntryRepository stockEntryRepository;
	@Autowired
	private StockIssueRepository stockIssueRepository;
	@Autowired
	private StockAdjustmentRepository stockAdjustmentRepository;
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
		RoleService roleService = new RoleService(roleRepository, permissionRepository, auditLogService);
		FeeService feeService = new FeeService(feeCategoryRepository, feeStructureGroupRepository, feeStructureRepository,
				feeStructureInstallmentRepository, studentFeeAssignmentRepository, invoiceRepository, paymentRepository,
				paymentAllocationRepository, refundRepository, adjustmentRepository, waiverRepository, feeDocumentCounterRepository,
				organisationBrandingRepository);
		InventoryService inventoryService =
				new InventoryService(itemCategoryRepository, itemRepository, stockEntryRepository, stockIssueRepository, stockAdjustmentRepository);
		ExaminationService examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, marksEntryRegisterRepository, reportCardRepository,
				examinationSettingsRepository, auditLogService);
		schoolTemplateItemService = new SchoolTemplateItemService(schoolTemplateItemRepository, schoolTemplateMasterItemRepository);

		schoolTemplateService = new SchoolTemplateService(schoolTemplateItemService, academicService, gradeLevelRepository,
				departmentRepository, designationRepository, roleRepository, roleService, permissionRepository, feeService,
				feeCategoryRepository, inventoryService, itemCategoryRepository, examinationService, gradingScaleRepository);

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
		Object[][] bands = { { "A+", 0, 90.0 }, { "A", 1, 80.0 }, { "B+", 2, 70.0 }, { "B", 3, 60.0 }, { "C+", 4, 50.0 }, { "C", 5, 40.0 },
				{ "D", 6, 33.0 }, { "F", 7, 0.0 } };
		for (Object[] band : bands) {
			schoolTemplateMasterItemRepository.save(new SchoolTemplateMasterItem(SchoolTemplateItemCategory.GRADING_BAND, (String) band[0],
					null, null, (Integer) band[1], null, null, (Double) band[2], null, null));
		}
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
				null, null);
		assertThat(schoolTemplateItemService.list(SchoolTemplateItemCategory.DEPARTMENT))
				.extracting(SchoolTemplateItem::getName)
				.contains("Sports");

		schoolTemplateItemService.update(created.getId(), "Sports & Athletics", null, null, null, null, null, null, null, null);
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

		schoolTemplateItemService.delete(schoolTemplateItemRepository.findAll().get(0).getId());
		schoolTemplateItemService.ensureSeeded();

		assertThat(schoolTemplateItemRepository.findAll()).hasSize(seededCount - 1);
	}
}
