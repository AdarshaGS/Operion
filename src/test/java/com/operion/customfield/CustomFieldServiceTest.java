package com.operion.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves #146's custom fields mechanism end to end: a definition validates its own
 * required/ACTIVE rules, and a Student admitted/updated with values actually persists and
 * reloads them through the entity's own {@code custom_fields} JSON column.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomFieldServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

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

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	private CustomFieldService customFieldService;
	private StudentService studentService;

	@BeforeEach
	void setUp() {
		customFieldService = new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper());
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, studentIdGenerator,
				new AuditLogService(auditLogRepository, new ObjectMapper()), customFieldService);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation newOrg(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		return organisation;
	}

	@Test
	void onlyActiveDefinedKeysAreKeptAndBlankOnesAreDropped() {
		newOrg("cf-filter-school");
		customFieldDefinitionRepository.save(
				new CustomFieldDefinition("STUDENT", "blood_group", "Blood group", CustomFieldDataType.TEXT, null, false));
		CustomFieldDefinition archived = customFieldDefinitionRepository.save(
				new CustomFieldDefinition("STUDENT", "old_field", "Old field", CustomFieldDataType.TEXT, null, false));
		archived.changeStatus(CustomFieldDefinitionStatus.ARCHIVED);
		customFieldDefinitionRepository.save(archived);

		String json = customFieldService.validateAndSerialize("STUDENT",
				Map.of("blood_group", "O+", "old_field", "should be dropped", "unknown_key", "should be dropped too", "extra", ""));

		Map<String, Object> deserialized = customFieldService.deserialize(json);
		assertThat(deserialized).containsExactly(Map.entry("blood_group", "O+"));
	}

	@Test
	void missingRequiredFieldIsRejected() {
		newOrg("cf-required-school");
		customFieldDefinitionRepository.save(
				new CustomFieldDefinition("STUDENT", "transport_route", "Transport route", CustomFieldDataType.TEXT, null, true));

		assertThatThrownBy(() -> customFieldService.validateAndSerialize("STUDENT", Map.of()))
				.isInstanceOf(com.operion.common.InvalidRequestException.class)
				.hasMessageContaining("Transport route");
	}

	@Test
	void noValuesSerializeToNullNotAnEmptyObject() {
		newOrg("cf-empty-school");
		assertThat(customFieldService.validateAndSerialize("STUDENT", null)).isNull();
		assertThat(customFieldService.validateAndSerialize("STUDENT", Map.of())).isNull();
		assertThat(customFieldService.deserialize(null)).isEmpty();
	}

	@Test
	void admittingAndUpdatingAStudentPersistsAndReplacesCustomFieldValues() {
		newOrg("cf-student-school");
		customFieldDefinitionRepository.save(
				new CustomFieldDefinition("STUDENT", "blood_group", "Blood group", CustomFieldDataType.TEXT, null, false));

		Person person = personRepository.save(new Person("Custom", "Fields"));
		Student student = studentService.admit(person, "ADM-300", LocalDate.of(2026, 5, 1), null, null, null, null,
				null, null, null, null, null, null, null, Map.of("blood_group", "O+"));

		assertThat(customFieldService.deserialize(student.getCustomFields())).containsExactly(Map.entry("blood_group", "O+"));

		Student updated = studentService.update(student, null, null, null, null, null, null, null, null, null, null,
				null, Map.of("blood_group", "AB+"));

		Student reloaded = studentRepository.findById(updated.getId()).orElseThrow();
		assertThat(customFieldService.deserialize(reloaded.getCustomFields())).containsExactly(Map.entry("blood_group", "AB+"));
	}
}
