package com.operion.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.document.api.IdCardRenderResponse;
import com.operion.document.api.IdCardRenderResponse.RenderedElement;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Proves render() resolves DATA_FIELD/QR_CODE/PHOTO bindings against a real student and
 * passes decorative elements (HEADER_BAND) through untouched. Per #33. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdCardTemplateServiceTest {

	@Autowired
	private IdCardTemplateRepository idCardTemplateRepository;
	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private CampusRepository campusRepository;
	@Autowired
	private AcademicYearRepository academicYearRepository;
	@Autowired
	private GradeLevelRepository gradeLevelRepository;
	@Autowired
	private SchoolClassRepository schoolClassRepository;
	@Autowired
	private SectionRepository sectionRepository;
	@Autowired
	private PersonRepository personRepository;
	@Autowired
	private StudentRepository studentRepository;
	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;
	@Autowired
	private StudentDocumentRepository studentDocumentRepository;
	@Autowired
	private StudentExitRepository studentExitRepository;
	@Autowired
	private StudentIdGenerator studentIdGenerator;
	@Autowired
	private AuditLogRepository auditLogRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void resolvesDataFieldsPhotoAndPassesDecorativeElementsThrough() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));

		Person person = new Person("Meera", "Nair");
		person.setPhotoUrl("https://example.com/photo.jpg");
		person = personRepository.save(person);

		StudentService studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
		Student student = studentService.admit(
				person, "ADM-001", LocalDate.of(2025, 5, 1), null, null, null, null, "O+", null, null, null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));
		assertThat(enrollment).isNotNull();

		String layoutJson = """
				{
				  "elements": [
				    {"id":"name","type":"DATA_FIELD","field":"fullName","x":5,"y":5,"width":100,"height":10},
				    {"id":"adm","type":"QR_CODE","field":"admissionNumber","x":5,"y":50,"width":20,"height":20},
				    {"id":"cls","type":"DATA_FIELD","field":"className","x":5,"y":20,"width":100,"height":10},
				    {"id":"sec","type":"DATA_FIELD","field":"section","x":5,"y":30,"width":100,"height":10},
				    {"id":"photo","type":"PHOTO","x":70,"y":5,"width":25,"height":25},
				    {"id":"band","type":"HEADER_BAND","x":0,"y":0,"width":100,"height":4}
				  ]
				}
				""";
		IdCardTemplate template = idCardTemplateRepository.save(new IdCardTemplate("Standard Card", 85.6, 54, layoutJson));

		IdCardTemplateService service = new IdCardTemplateService(idCardTemplateRepository, studentRepository, studentEnrollmentRepository,
				objectMapper);

		IdCardRenderResponse response = service.render(template.getId(), student.getId());

		assertThat(response.widthMm()).isEqualTo(85.6);
		assertThat(response.elements()).hasSize(6);
		assertThat(element(response, "name").value()).isEqualTo("Meera Nair");
		assertThat(element(response, "adm").value()).isEqualTo("ADM-001");
		assertThat(element(response, "cls").value()).isEqualTo("Grade 5");
		assertThat(element(response, "sec").value()).isEqualTo("A");
		assertThat(element(response, "photo").photoUrl()).isEqualTo("https://example.com/photo.jpg");
		assertThat(element(response, "band").value()).isNull();
	}

	private RenderedElement element(IdCardRenderResponse response, String id) {
		return response.elements().stream().filter(e -> e.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no element " + id));
	}
}
