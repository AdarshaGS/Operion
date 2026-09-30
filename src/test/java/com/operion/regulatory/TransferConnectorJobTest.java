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
import com.operion.student.StudentExit;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentExitType;
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

/** Proves #235: jobs are only prepared for TRANSFER exits, the payload comes from the chosen
 * student schema, and the lifecycle is one-way with terminal SUBMITTED/CANCELLED. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransferConnectorJobTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentExitRepository studentExitRepository;

	@Autowired
	private StudentEnrollmentRepository enrollmentRepository;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

	@Autowired
	private RegulatoryExportSchemaRepository schemaRepository;

	@Autowired
	private RegulatoryExportFieldRepository fieldRepository;

	@Autowired
	private TransferConnectorJobRepository jobRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private TransferConnectorService service;
	private RegulatoryExportSchema schema;
	private Student student;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void setUp(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		ObjectMapper objectMapper = new ObjectMapper();
		AuditLogService auditLogService = new AuditLogService(auditLogRepository, objectMapper);
		RegulatoryExportService exportService = new RegulatoryExportService(schemaRepository, fieldRepository, studentRepository,
				enrollmentRepository, staffProfileRepository, auditLogService, objectMapper);
		service = new TransferConnectorService(jobRepository, exportService, auditLogService, objectMapper);

		schema = exportService.createSchema("XFER", "Transfer record", null, RegulatoryEntityType.STUDENT);
		exportService.replaceFields(schema, List.of(new FieldSpec("adm", "admissionNumber", null),
				new FieldSpec("name", "fullName", null)));
		Person person = personRepository.save(new Person("Anaya", "Rao"));
		student = studentRepository.save(new Student(person, "STU-1", "ADM-1", LocalDate.of(2025, 5, 1), null, null, null, null,
				null, null, null, null, null, null, null, null));
	}

	private StudentExit exit(StudentExitType type) {
		return studentExitRepository.save(new StudentExit(student, type, LocalDate.of(2026, 3, 31), null, "Elsewhere School", 1L));
	}

	@Test
	void preparingBuildsThePayloadFromTheSchema() {
		setUp("xfer-prepare");

		TransferConnectorJob job = service.prepare(exit(StudentExitType.TRANSFER), schema, " Records portal ");

		assertThat(job.getStatus()).isEqualTo(TransferConnectorJobStatus.PREPARED);
		assertThat(job.getConnectorName()).isEqualTo("Records portal");
		assertThat(job.getPayload()).isEqualTo("{\"adm\":\"ADM-1\",\"name\":\"Anaya Rao\"}");
		assertThat(jobRepository.findByStudentExitIdOrderByIdDesc(job.getStudentExit().getId())).hasSize(1);
	}

	@Test
	void onlyTransferExitsAreAccepted() {
		setUp("xfer-type");

		assertThatThrownBy(() -> service.prepare(exit(StudentExitType.WITHDRAWAL), schema, "Portal"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(jobRepository.findAll()).isEmpty();
	}

	@Test
	void aStaffSchemaCannotBeUsedForAStudentPayload() {
		setUp("xfer-staff-schema");
		RegulatoryExportSchema staffSchema = schemaRepository.save(new RegulatoryExportSchema("STAFF1", "Staff", null, RegulatoryEntityType.STAFF));

		assertThatThrownBy(() -> service.prepare(exit(StudentExitType.TRANSFER), staffSchema, "Portal"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void submittedJobsRecordTheReferenceAndAreTerminal() {
		setUp("xfer-submit");
		TransferConnectorJob job = service.prepare(exit(StudentExitType.TRANSFER), schema, "Portal");

		service.markSubmitted(job, "REF-42");

		TransferConnectorJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(TransferConnectorJobStatus.SUBMITTED);
		assertThat(reloaded.getExternalReference()).isEqualTo("REF-42");
		assertThat(reloaded.getSubmittedAt()).isNotNull();
		assertThatThrownBy(() -> service.markSubmitted(reloaded, "again")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> service.cancel(reloaded)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void failedJobsNeedAReasonAndCanBeReprepared() {
		setUp("xfer-fail");
		TransferConnectorJob job = service.prepare(exit(StudentExitType.TRANSFER), schema, "Portal");

		assertThatThrownBy(() -> service.markFailed(job, " ")).isInstanceOf(IllegalArgumentException.class);
		service.markFailed(job, "Rejected: missing field");
		assertThat(jobRepository.findById(job.getId()).orElseThrow().getFailureReason()).isEqualTo("Rejected: missing field");
		assertThatThrownBy(() -> service.markSubmitted(job, "x")).isInstanceOf(IllegalStateException.class);

		service.reprepare(job);

		TransferConnectorJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(TransferConnectorJobStatus.PREPARED);
		assertThat(reloaded.getFailureReason()).isNull();
		assertThatThrownBy(() -> service.reprepare(reloaded)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void everyTransitionIsAudited() {
		setUp("xfer-audit");
		TransferConnectorJob job = service.prepare(exit(StudentExitType.TRANSFER), schema, "Portal");
		service.cancel(job);

		assertThat(auditLogRepository.findAll()).filteredOn(l -> "TRANSFER_CONNECTOR_JOB".equals(l.getEntityType()) && TenantContext.getOrganisationId().equals(l.getOrganisationId()))
				.extracting(l -> l.getAction()).containsExactly("PREPARED", "CANCELLED");
	}
}
