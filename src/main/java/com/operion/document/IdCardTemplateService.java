package com.operion.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.operion.academic.SchoolClass;
import com.operion.document.api.IdCardRenderResponse;
import com.operion.document.api.IdCardRenderResponse.RenderedElement;
import com.operion.identity.Person;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves an IdCardTemplate's layoutJson ("elements": [{type, field, ...}]) against one
 * student's real data. Only DATA_FIELD/QR_CODE (via their "field" key) and PHOTO elements
 * carry a binding; every other element type passes through geometry untouched. Per #33.
 */
@Service
public class IdCardTemplateService {

	private final IdCardTemplateRepository idCardTemplateRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final ObjectMapper objectMapper;

	public IdCardTemplateService(IdCardTemplateRepository idCardTemplateRepository, StudentRepository studentRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, ObjectMapper objectMapper) {
		this.idCardTemplateRepository = idCardTemplateRepository;
		this.studentRepository = studentRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.objectMapper = objectMapper;
	}

	public IdCardRenderResponse render(Long templateId, Long studentId) {
		IdCardTemplate template = idCardTemplateRepository.findById(templateId)
				.orElseThrow(() -> new IllegalArgumentException("No ID card template with id " + templateId));
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
		Optional<StudentEnrollment> enrollment = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(studentId);

		List<RenderedElement> resolved = new ArrayList<>();
		JsonNode elements = objectMapper.readTree(template.getLayoutJson()).path("elements");
		if (elements.isArray()) {
			for (JsonNode element : elements) {
				resolved.add(resolveElement(element, student, enrollment));
			}
		}
		return new IdCardRenderResponse(template.getId(), String.valueOf(student.getId()), template.getWidthMm(), template.getHeightMm(),
				resolved);
	}

	private RenderedElement resolveElement(JsonNode element, Student student, Optional<StudentEnrollment> enrollment) {
		String id = element.path("id").asString(null);
		String type = element.path("type").asString("");
		double x = element.path("x").asDouble(0);
		double y = element.path("y").asDouble(0);
		double width = element.path("width").asDouble(0);
		double height = element.path("height").asDouble(0);

		String value = switch (type) {
			case "TEXT" -> element.path("text").asString(null);
			case "DATA_FIELD", "QR_CODE" -> resolveField(element.path("field").asString(""), student, enrollment);
			default -> null;
		};
		String photoUrl = "PHOTO".equals(type) ? student.getPerson().getPhotoUrl() : null;

		return new RenderedElement(id, type, x, y, width, height, value, photoUrl);
	}

	private String resolveField(String field, Student student, Optional<StudentEnrollment> enrollment) {
		Person person = student.getPerson();
		return switch (field) {
			case "fullName" -> fullName(person);
			case "admissionNumber" -> student.getAdmissionNumber();
			case "bloodGroup" -> student.getBloodGroup();
			case "dateOfBirth" -> person.getDateOfBirth() == null ? null : person.getDateOfBirth().toString();
			case "className" -> enrollment.map(e -> className(e.getSection().getSchoolClass())).orElse(null);
			case "section" -> enrollment.map(e -> e.getSection().getName()).orElse(null);
			default -> null;
		};
	}

	private String fullName(Person person) {
		return person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
	}

	private String className(SchoolClass schoolClass) {
		return schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
	}
}
