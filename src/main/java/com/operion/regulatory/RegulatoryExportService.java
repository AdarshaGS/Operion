package com.operion.regulatory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.operion.audit.AuditLogService;
import com.operion.common.CsvUtil;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.regulatory.RegulatoryExportSources.Source;
import com.operion.regulatory.RegulatoryExportSources.StaffContext;
import com.operion.regulatory.RegulatoryExportSources.StudentContext;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cross-cutting like dashboard/FeeReminderService: reads student and HR data through their
 * own repositories to render an org-defined {@link RegulatoryExportSchema}. Owns no student
 * or HR rules - it only maps fields, so a new regional layout is configuration, not code.
 */
@Service
public class RegulatoryExportService {

	public record FieldSpec(String columnHeader, String sourceField, String defaultValue) {
	}

	private static final List<StudentStatus> EXPORTED_STUDENT_STATUSES = List.of(StudentStatus.ADMITTED, StudentStatus.ACTIVE);
	private static final List<StaffProfileStatus> EXPORTED_STAFF_STATUSES = List.of(StaffProfileStatus.ACTIVE, StaffProfileStatus.ON_LEAVE);

	private final RegulatoryExportSchemaRepository schemaRepository;
	private final RegulatoryExportFieldRepository fieldRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final AuditLogService auditLogService;
	private final ObjectMapper objectMapper;

	public RegulatoryExportService(RegulatoryExportSchemaRepository schemaRepository,
			RegulatoryExportFieldRepository fieldRepository, StudentRepository studentRepository,
			StudentEnrollmentRepository enrollmentRepository, StaffProfileRepository staffProfileRepository,
			AuditLogService auditLogService, ObjectMapper objectMapper) {
		this.schemaRepository = schemaRepository;
		this.fieldRepository = fieldRepository;
		this.studentRepository = studentRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.auditLogService = auditLogService;
		this.objectMapper = objectMapper;
	}

	public RegulatoryExportSchema createSchema(String code, String name, String description, RegulatoryEntityType entityType) {
		if (code == null || code.isBlank() || name == null || name.isBlank()) {
			throw new IllegalArgumentException("Code and name are required");
		}
		if (schemaRepository.findByCode(code.trim()).isPresent()) {
			throw new IllegalStateException("An export schema with code '" + code.trim() + "' already exists");
		}
		RegulatoryExportSchema schema = schemaRepository.save(
				new RegulatoryExportSchema(code.trim(), name.trim(), description, entityType));
		auditLogService.record("REGULATORY_EXPORT_SCHEMA", schema.getId(), "CREATED", null, schema.getCode());
		return schema;
	}

	public RegulatoryExportSchema updateSchema(RegulatoryExportSchema schema, String name, String description) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Name is required");
		}
		schema.update(name.trim(), description);
		return schemaRepository.save(schema);
	}

	public RegulatoryExportSchema changeStatus(RegulatoryExportSchema schema, RegulatoryExportStatus status) {
		schema.changeStatus(status);
		return schemaRepository.save(schema);
	}

	/** Replaces the whole column list - a mapping is edited as a unit, so partial edits would
	 * only make the ordinals harder to keep coherent. */
	@Transactional
	public List<RegulatoryExportField> replaceFields(RegulatoryExportSchema schema, List<FieldSpec> specs) {
		if (specs == null || specs.isEmpty()) {
			throw new IllegalArgumentException("An export schema needs at least one column");
		}
		Set<String> headers = new HashSet<>();
		for (FieldSpec spec : specs) {
			if (spec.columnHeader() == null || spec.columnHeader().isBlank()) {
				throw new IllegalArgumentException("Every column needs a header");
			}
			if (!headers.add(spec.columnHeader().trim())) {
				throw new IllegalArgumentException("Duplicate column header '" + spec.columnHeader().trim() + "'");
			}
			requireKnownSource(schema.getEntityType(), spec.sourceField());
		}
		fieldRepository.deleteAll(fieldRepository.findBySchemaIdOrderByOrdinal(schema.getId()));
		fieldRepository.flush();
		List<RegulatoryExportField> saved = new ArrayList<>();
		for (int i = 0; i < specs.size(); i++) {
			FieldSpec spec = specs.get(i);
			saved.add(fieldRepository.save(new RegulatoryExportField(schema, i + 1, spec.columnHeader().trim(),
					spec.sourceField().trim(), blankToNull(spec.defaultValue()))));
		}
		auditLogService.record("REGULATORY_EXPORT_SCHEMA", schema.getId(), "FIELDS_REPLACED", null,
				saved.stream().map(RegulatoryExportField::getSourceField).toList());
		return saved;
	}

	public List<RegulatoryExportField> fieldsOf(RegulatoryExportSchema schema) {
		return fieldRepository.findBySchemaIdOrderByOrdinal(schema.getId());
	}

	/** One header line then one line per record in scope: ADMITTED/ACTIVE students or
	 * ACTIVE/ON_LEAVE staff (people who've left are not part of a current return). Not read-only:
	 * every export writes an audit row, which a read-only connection refuses. */
	@Transactional
	public String exportCsv(RegulatoryExportSchema schema) {
		List<RegulatoryExportField> fields = requireExportable(schema);
		List<String> lines = new ArrayList<>();
		lines.add(CsvUtil.formatLine(fields.stream().map(RegulatoryExportField::getColumnHeader).toList()));
		int rows = 0;
		if (schema.getEntityType() == RegulatoryEntityType.STUDENT) {
			List<Student> students = studentRepository.findByStatusIn(EXPORTED_STUDENT_STATUSES);
			Map<Long, StudentEnrollment> enrollments = currentEnrollments(students);
			for (Student student : students) {
				lines.add(CsvUtil.formatLine(List.copyOf(studentRecord(fields, student, enrollments.get(student.getId())).values())));
				rows++;
			}
		} else {
			for (StaffProfile staff : staffProfileRepository.findByStatusIn(EXPORTED_STAFF_STATUSES)) {
				lines.add(CsvUtil.formatLine(List.copyOf(staffRecord(fields, staff).values())));
				rows++;
			}
		}
		auditLogService.record("REGULATORY_EXPORT_SCHEMA", schema.getId(), "EXPORTED", null, Map.of("rows", rows));
		return String.join("\r\n", lines) + "\r\n";
	}

	/** Header -> value for a single student, in column order. Backs connector payloads. */
	@Transactional(readOnly = true)
	public Map<String, String> buildStudentRecord(RegulatoryExportSchema schema, Student student) {
		if (schema.getEntityType() != RegulatoryEntityType.STUDENT) {
			throw new IllegalStateException("Export schema '" + schema.getCode() + "' does not describe students");
		}
		List<RegulatoryExportField> fields = requireExportable(schema);
		StudentEnrollment enrollment = enrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).orElse(null);
		return studentRecord(fields, student, enrollment);
	}

	private List<RegulatoryExportField> requireExportable(RegulatoryExportSchema schema) {
		if (schema.getStatus() != RegulatoryExportStatus.ACTIVE) {
			throw new IllegalStateException("Export schema '" + schema.getCode() + "' is inactive");
		}
		List<RegulatoryExportField> fields = fieldsOf(schema);
		if (fields.isEmpty()) {
			throw new IllegalStateException("Export schema '" + schema.getCode() + "' has no columns yet");
		}
		return fields;
	}

	private Map<Long, StudentEnrollment> currentEnrollments(List<Student> students) {
		if (students.isEmpty()) {
			return Map.of();
		}
		return enrollmentRepository.findByStudentIdInAndCurrentTrue(students.stream().map(Student::getId).toList()).stream()
				.collect(Collectors.toMap(e -> e.getStudent().getId(), Function.identity(), (a, b) -> a));
	}

	private Map<String, String> studentRecord(List<RegulatoryExportField> fields, Student student, StudentEnrollment enrollment) {
		StudentContext context = new StudentContext(student, enrollment);
		Map<String, Source<StudentContext>> catalog = RegulatoryExportSources.forType(RegulatoryEntityType.STUDENT);
		JsonNode custom = parseCustomFields(student.getCustomFields());
		Map<String, String> record = new LinkedHashMap<>();
		for (RegulatoryExportField field : fields) {
			String value;
			if (field.getSourceField().startsWith(RegulatoryExportSources.CUSTOM_PREFIX)) {
				JsonNode node = custom == null ? null
						: custom.get(field.getSourceField().substring(RegulatoryExportSources.CUSTOM_PREFIX.length()));
				value = node == null || node.isNull() ? "" : node.asString();
			} else {
				value = catalog.get(field.getSourceField()).extractor().apply(context);
			}
			record.put(field.getColumnHeader(), orDefault(value, field));
		}
		return record;
	}

	private Map<String, String> staffRecord(List<RegulatoryExportField> fields, StaffProfile staff) {
		StaffContext context = new StaffContext(staff);
		Map<String, Source<StaffContext>> catalog = RegulatoryExportSources.forType(RegulatoryEntityType.STAFF);
		Map<String, String> record = new LinkedHashMap<>();
		for (RegulatoryExportField field : fields) {
			record.put(field.getColumnHeader(), orDefault(catalog.get(field.getSourceField()).extractor().apply(context), field));
		}
		return record;
	}

	private JsonNode parseCustomFields(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(json);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private void requireKnownSource(RegulatoryEntityType type, String sourceField) {
		if (sourceField == null || sourceField.isBlank()) {
			throw new IllegalArgumentException("Every column needs a source field");
		}
		String key = sourceField.trim();
		if (key.startsWith(RegulatoryExportSources.CUSTOM_PREFIX)) {
			if (type != RegulatoryEntityType.STUDENT || key.length() == RegulatoryExportSources.CUSTOM_PREFIX.length()) {
				throw new IllegalArgumentException("Custom fields are only available on student schemas, as custom:<fieldKey>");
			}
			return;
		}
		if (!RegulatoryExportSources.forType(type).containsKey(key)) {
			throw new IllegalArgumentException("Unknown source field '" + key + "' for " + type);
		}
	}

	private static String orDefault(String value, RegulatoryExportField field) {
		return (value == null || value.isBlank()) && field.getDefaultValue() != null ? field.getDefaultValue() : value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
