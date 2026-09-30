package com.operion.regulatory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.regulatory.RegulatoryExportService.FieldSpec;
import com.operion.student.Student;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the #233 framework: a schema is pure configuration over the source-field catalog, the
 * export honours column order/defaults/custom fields, and nothing regional is seeded. The
 * service is constructed by hand (real repos, real AuditLogService) - AuditLogService can't be
 * @Import-ed into @DataJpaTest, which has no ObjectMapper bean.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegulatoryExportTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository enrollmentRepository;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

	@Autowired
	private RegulatoryExportSchemaRepository schemaRepository;

	@Autowired
	private RegulatoryExportFieldRepository fieldRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private RegulatoryExportService service;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void setUp(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		ObjectMapper objectMapper = new ObjectMapper();
		service = new RegulatoryExportService(schemaRepository, fieldRepository, studentRepository, enrollmentRepository,
				staffProfileRepository, new AuditLogService(auditLogRepository, objectMapper), objectMapper);
	}

	private Student student(String firstName, String lastName, String studentId, String customFields) {
		Person person = personRepository.save(new Person(firstName, lastName));
		return studentRepository.save(new Student(person, studentId, "ADM-" + studentId, LocalDate.of(2025, 5, 1), null, null,
				null, null, null, null, null, null, null, null, null, customFields));
	}

	@Test
	void nothingIsSeededForANewOrganisation() {
		setUp("reg-empty");
		assertThat(schemaRepository.findAll()).isEmpty();
	}

	@Test
	void duplicateSchemaCodeIsRejected() {
		setUp("reg-dup");
		service.createSchema("ANNUAL_RETURN", "Annual return", null, RegulatoryEntityType.STUDENT);

		assertThatThrownBy(() -> service.createSchema("ANNUAL_RETURN", "Again", null, RegulatoryEntityType.STAFF))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unknownSourceFieldsAreRejected() {
		setUp("reg-unknown");
		RegulatoryExportSchema schema = service.createSchema("S1", "S1", null, RegulatoryEntityType.STUDENT);

		assertThatThrownBy(() -> service.replaceFields(schema, List.of(new FieldSpec("Col", "notAField", null))))
				.isInstanceOf(IllegalArgumentException.class);
		// staff catalog has no student-only fields, and custom: is student-only
		RegulatoryExportSchema staffSchema = service.createSchema("S2", "S2", null, RegulatoryEntityType.STAFF);
		assertThatThrownBy(() -> service.replaceFields(staffSchema, List.of(new FieldSpec("Col", "admissionNumber", null))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.replaceFields(staffSchema, List.of(new FieldSpec("Col", "custom:x", null))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void duplicateHeadersAndEmptyColumnListsAreRejected() {
		setUp("reg-headers");
		RegulatoryExportSchema schema = service.createSchema("S1", "S1", null, RegulatoryEntityType.STUDENT);

		assertThatThrownBy(() -> service.replaceFields(schema, List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.replaceFields(schema,
				List.of(new FieldSpec("Name", "firstName", null), new FieldSpec("Name", "lastName", null))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.exportCsv(schema)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void replacingFieldsRewritesTheColumnListInOrder() {
		setUp("reg-replace");
		RegulatoryExportSchema schema = service.createSchema("S1", "S1", null, RegulatoryEntityType.STUDENT);
		service.replaceFields(schema, List.of(new FieldSpec("A", "firstName", null), new FieldSpec("B", "lastName", null)));

		service.replaceFields(schema, List.of(new FieldSpec("Adm", "admissionNumber", null)));

		assertThat(service.fieldsOf(schema)).extracting(RegulatoryExportField::getColumnHeader).containsExactly("Adm");
		assertThat(fieldRepository.findAll()).hasSize(1);
	}

	@Test
	void studentExportFollowsColumnOrderDefaultsAndCustomFields() {
		setUp("reg-export");
		student("Anaya", "Rao", "STU-1", "{\"house\":\"Red\"}");
		student("Bilal", "Khan, Jr", "STU-2", null);
		RegulatoryExportSchema schema = service.createSchema("RETURN", "Return", null, RegulatoryEntityType.STUDENT);
		service.replaceFields(schema, List.of(
				new FieldSpec("Adm No", "admissionNumber", null),
				new FieldSpec("Last", "lastName", null),
				new FieldSpec("Class", "className", "UNPLACED"),
				new FieldSpec("House", "custom:house", "-")));

		String csv = service.exportCsv(schema);

		List<String> lines = csv.lines().toList();
		assertThat(lines.get(0)).isEqualTo("Adm No,Last,Class,House");
		assertThat(lines).contains("ADM-STU-1,Rao,UNPLACED,Red");
		assertThat(lines).contains("ADM-STU-2,\"Khan, Jr\",UNPLACED,-");
		assertThat(lines).hasSize(3);
	}

	@Test
	void inactiveSchemasCannotBeExportedAndExportsAreAudited() {
		setUp("reg-inactive");
		student("Anaya", "Rao", "STU-1", null);
		RegulatoryExportSchema schema = service.createSchema("S1", "S1", null, RegulatoryEntityType.STUDENT);
		service.replaceFields(schema, List.of(new FieldSpec("First", "firstName", null)));
		service.exportCsv(schema);

		assertThat(auditLogRepository.findAll()).anyMatch(l -> "EXPORTED".equals(l.getAction())
				&& "REGULATORY_EXPORT_SCHEMA".equals(l.getEntityType()));

		service.changeStatus(schema, RegulatoryExportStatus.INACTIVE);
		assertThatThrownBy(() -> service.exportCsv(schema)).isInstanceOf(IllegalStateException.class);
	}
}
