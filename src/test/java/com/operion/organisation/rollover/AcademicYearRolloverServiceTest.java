package com.operion.organisation.rollover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.operion.academic.ClassStructureRollover;
import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentRollover;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.academic.TeacherAssignmentType;
import com.operion.academic.YearStructureLookup;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeCategoryType;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureGroup;
import com.operion.finance.FeeStructureGroupRepository;
import com.operion.finance.FeeStructureInstallment;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.finance.FeeStructureRollover;
import com.operion.finance.PaymentFrequency;
import com.operion.hr.LeaveAllocationRollover;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.timetable.Period;
import com.operion.timetable.PeriodRepository;
import com.operion.timetable.TimetableEntry;
import com.operion.timetable.TimetableEntryRepository;
import com.operion.timetable.TimetableRollover;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Real rows end to end (no mocks): a source year with a class, sections, a subject, a teacher,
 * a timetable slot and a fee structure is copied into the next year. Covers that the preview
 * writes nothing yet reports what apply then does, that a second run adds nothing, that steps
 * work in any order and that a module switched off is not offered.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, AcademicYearRolloverService.class, ClassStructureRollover.class,
		TeacherAssignmentRollover.class, TimetableRollover.class, FeeStructureRollover.class, LeaveAllocationRollover.class,
		YearStructureLookup.class, ModuleAccessService.class, AcademicYearRolloverServiceTest.Audit.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AcademicYearRolloverServiceTest {

	@TestConfiguration
	static class Audit {
		@Bean
		AuditLogService auditLogService(AuditLogRepository repository) {
			return new AuditLogService(repository, new ObjectMapper());
		}
	}

	@Autowired private AcademicYearRolloverService service;
	@Autowired private ModuleAccessService moduleAccessService;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private AcademicYearRepository yearRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository classRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private ClassSubjectRepository classSubjectRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private TeacherAssignmentRepository assignmentRepository;
	@Autowired private PeriodRepository periodRepository;
	@Autowired private TimetableEntryRepository timetableRepository;
	@Autowired private FeeCategoryRepository feeCategoryRepository;
	@Autowired private FeeStructureGroupRepository groupRepository;
	@Autowired private FeeStructureRepository structureRepository;
	@Autowired private FeeStructureInstallmentRepository installmentRepository;

	private AcademicYear source;
	private AcademicYear target;
	private Organisation organisation;

	@BeforeEach
	void setUp() {
		TenantContext.clear();
		organisation = organisationRepository.save(new Organisation("Rollover School", "Rollover Trust", "rollover-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);

		source = yearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		target = yearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = classRepository.save(new SchoolClass(source, campus, grade5, null));
		Section sectionA = sectionRepository.save(new Section(schoolClass, "A", 40, "Room 1"));
		sectionRepository.save(new Section(schoolClass, "B", 40, null));
		Subject maths = subjectRepository.save(new Subject("Maths", "MATH"));
		classSubjectRepository.save(new ClassSubject(schoolClass, maths, true));
		Person teacher = personRepository.save(new Person("Anil", "Rao"));
		assignmentRepository.save(new TeacherAssignment(source, sectionA, maths, teacher, TeacherAssignmentType.SUBJECT, source.getStartDate()));
		Period period = periodRepository.save(new Period(1, "P1", LocalTime.of(9, 0), LocalTime.of(9, 45), false));
		timetableRepository.save(new TimetableEntry(source, sectionA, maths, teacher, DayOfWeek.MONDAY, period));

		FeeCategory tuition = feeCategoryRepository.save(new FeeCategory("TUITION", "Tuition", null, FeeCategoryType.GENERAL));
		FeeStructureGroup group = groupRepository.save(new FeeStructureGroup("Standard", source, schoolClass));
		FeeStructure line = structureRepository.save(new FeeStructure(group, tuition, new BigDecimal("1000.00"), PaymentFrequency.QUARTERLY, null));
		installmentRepository.save(new FeeStructureInstallment(line, 1, LocalDate.of(2025, 6, 10), new BigDecimal("333.33")));
		installmentRepository.save(new FeeStructureInstallment(line, 2, LocalDate.of(2025, 9, 10), new BigDecimal("333.33")));
		installmentRepository.save(new FeeStructureInstallment(line, 3, LocalDate.of(2025, 12, 10), new BigDecimal("333.34")));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private RolloverRequest request(String... steps) {
		return new RolloverRequest(source.getId(), Set.of(steps), null);
	}

	private static RolloverStep step(RolloverReport report, String key) {
		return report.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
	}

	private static int copied(RolloverStep step, String item) {
		return step.items().stream().filter(i -> i.name().equals(item)).findFirst().orElseThrow().copied();
	}

	@Test
	void previewReportsEverythingButSavesNothing() {
		RolloverReport report = service.preview(target.getId(), request());

		assertThat(report.dryRun()).isTrue();
		assertThat(copied(step(report, "CLASSES"), "Classes")).isEqualTo(1);
		assertThat(copied(step(report, "CLASSES"), "Sections")).isEqualTo(2);
		assertThat(copied(step(report, "CLASSES"), "Class subjects")).isEqualTo(1);
		// Later steps see what earlier steps would create, even though nothing is saved.
		assertThat(copied(step(report, "TEACHER_ASSIGNMENTS"), "Assignments")).isEqualTo(1);
		assertThat(copied(step(report, "TIMETABLE"), "Timetable slots")).isEqualTo(1);
		assertThat(copied(step(report, "FEE_STRUCTURES"), "Installments")).isEqualTo(3);

		assertThat(classRepository.findByAcademicYearId(target.getId())).isEmpty();
		assertThat(groupRepository.findByAcademicYearId(target.getId())).isEmpty();
		assertThat(assignmentRepository.findByAcademicYearIdAndStatus(target.getId(), TeacherAssignmentStatus.ACTIVE)).isEmpty();
	}

	@Test
	void applyCopiesTheStructureAndAppliesTheFeeIncrease() {
		RolloverReport report = service.apply(target.getId(), new RolloverRequest(source.getId(), null, new BigDecimal("10")));

		assertThat(report.dryRun()).isFalse();
		List<SchoolClass> classes = classRepository.findByAcademicYearId(target.getId());
		assertThat(classes).hasSize(1);
		assertThat(sectionRepository.findBySchoolClassId(classes.get(0).getId())).extracting(Section::getName).containsExactlyInAnyOrder("A", "B");
		assertThat(classSubjectRepository.findBySchoolClassId(classes.get(0).getId())).hasSize(1);

		TeacherAssignment assignment = assignmentRepository.findByAcademicYearIdAndStatus(target.getId(), TeacherAssignmentStatus.ACTIVE).get(0);
		assertThat(assignment.getStartDate()).isEqualTo(target.getStartDate());
		assertThat(assignment.getSection().getName()).isEqualTo("A");
		assertThat(timetableRepository.findByAcademicYearId(target.getId())).hasSize(1);

		FeeStructureGroup group = groupRepository.findByAcademicYearId(target.getId()).get(0);
		FeeStructure line = structureRepository.findByFeeStructureGroupId(group.getId()).get(0);
		assertThat(line.getAmount()).isEqualByComparingTo("1100.00");
		assertThat(line.getPaymentFrequency()).isEqualTo(PaymentFrequency.QUARTERLY);
		List<FeeStructureInstallment> installments = installmentRepository.findByFeeStructureIdOrderByInstallmentNumber(line.getId());
		assertThat(installments).extracting(FeeStructureInstallment::getDueDate)
				.containsExactly(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 12, 10));
		// Rounding each installment must not leave the total off the line amount.
		assertThat(installments.stream().map(FeeStructureInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1100.00");
	}

	@Test
	void runningTwiceAddsNothingTheSecondTime() {
		service.apply(target.getId(), request());

		RolloverReport second = service.apply(target.getId(), request());

		assertThat(second.steps()).allSatisfy(step -> assertThat(step.copiedTotal()).as(step.key()).isZero());
		assertThat(classRepository.findByAcademicYearId(target.getId())).hasSize(1);
		assertThat(groupRepository.findByAcademicYearId(target.getId())).hasSize(1);
		assertThat(timetableRepository.findByAcademicYearId(target.getId())).hasSize(1);
	}

	@Test
	void stepsWorkInAnyOrderAndSayWhatIsMissing() {
		RolloverReport first = service.apply(target.getId(), request("FEE_STRUCTURES"));
		assertThat(step(first, "FEE_STRUCTURES").copiedTotal()).isZero();
		assertThat(step(first, "FEE_STRUCTURES").notes()).anyMatch(note -> note.contains("run the classes step first"));

		service.apply(target.getId(), request("CLASSES"));
		RolloverReport again = service.apply(target.getId(), request("FEE_STRUCTURES"));
		assertThat(copied(step(again, "FEE_STRUCTURES"), "Fee groups")).isEqualTo(1);
	}

	@Test
	void aSwitchedOffModuleIsNotOffered() {
		moduleAccessService.setEnabled(organisation.getId(), AppModule.FEES, false, "test");

		RolloverReport report = service.preview(target.getId(), request());

		assertThat(report.steps()).extracting(RolloverStep::key).doesNotContain("FEE_STRUCTURES");
		assertThat(report.unavailable()).contains("Fee structures");
		assertThat(service.availableSteps()).extracting(RolloverContributor::key).doesNotContain("FEE_STRUCTURES");
	}

	@Test
	void badRequestsAreRejected() {
		assertThatThrownBy(() -> service.preview(target.getId(), new RolloverRequest(target.getId(), null, null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.preview(target.getId(), request("NOT_A_STEP"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.preview(target.getId(), new RolloverRequest(source.getId(), null, new BigDecimal("900"))))
				.isInstanceOf(IllegalArgumentException.class);

		target.close();
		yearRepository.save(target);
		assertThatThrownBy(() -> service.preview(target.getId(), request())).isInstanceOf(IllegalStateException.class);
	}
}
