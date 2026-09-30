package com.operion.learning;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Learning materials repository (#228): class/subject-scoped notes, links and files. */
@Service
@Transactional
public class LearningMaterialService {

	private final LearningMaterialRepository materialRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final PersonRepository personRepository;

	public LearningMaterialService(LearningMaterialRepository materialRepository, SchoolClassRepository schoolClassRepository,
			SectionRepository sectionRepository, SubjectRepository subjectRepository, PersonRepository personRepository) {
		this.materialRepository = materialRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.personRepository = personRepository;
	}

	public LearningMaterial add(LearningActor actor, Long classId, Long subjectId, String title, String description,
			MaterialKind kind, String attachmentRef, String url) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Title is required");
		}
		if (kind == null) {
			throw new IllegalArgumentException("Kind is required");
		}
		String cleanRef = blankToNull(attachmentRef);
		String cleanUrl = blankToNull(url);
		switch (kind) {
			case FILE -> {
				if (cleanRef == null) {
					throw new IllegalArgumentException("Attach a file");
				}
				cleanUrl = null;
			}
			case LINK -> {
				if (cleanUrl == null || !(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://"))) {
					throw new IllegalArgumentException("A link must start with http:// or https://");
				}
				cleanRef = null;
			}
			case NOTE -> {
				if (blankToNull(description) == null) {
					throw new IllegalArgumentException("A note needs some text in the description");
				}
				cleanRef = null;
				cleanUrl = null;
			}
		}
		SchoolClass schoolClass = schoolClassRepository.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
		Subject subject = subjectId == null ? null : subjectRepository.findById(subjectId)
				.orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
		requireCanWriteClass(actor, classId, subjectId);
		return materialRepository.save(new LearningMaterial(schoolClass, subject, title.trim(), blankToNull(description), kind,
				cleanRef, cleanUrl, personRepository.getReferenceById(actor.personId())));
	}

	public LearningMaterial archive(LearningActor actor, Long materialId) {
		LearningMaterial material = materialRepository.findById(materialId)
				.orElseThrow(() -> new IllegalArgumentException("Material not found: " + materialId));
		requireCanWriteClass(actor, material.getSchoolClass().getId(), material.getSubject() == null ? null : material.getSubject().getId());
		material.archive();
		return material;
	}

	@Transactional(readOnly = true)
	public List<LearningMaterial> list(LearningActor actor, Long classId) {
		List<LearningMaterial> all;
		if (actor.seesAllSections()) {
			all = materialRepository.findAllByOrderByIdDesc();
		} else {
			all = materialRepository.findBySchoolClassIdInOrderByIdDesc(teachingClassIds(actor));
		}
		return classId == null ? all : all.stream().filter(m -> m.getSchoolClass().getId().equals(classId)).toList();
	}

	/** Active materials for the child's class - a subject-specific one shows for every section of it. */
	@Transactional(readOnly = true)
	public List<LearningMaterial> forChild(StudentEnrollment enrollment) {
		return materialRepository.findBySchoolClassIdAndStatusOrderByIdDesc(
				enrollment.getSection().getSchoolClass().getId(), MaterialStatus.ACTIVE);
	}

	/** A manager may write anywhere; a teacher needs an assignment in some section of the class,
	 * for the given subject (any subject when the material is class-wide, or they're homeroom). */
	private void requireCanWriteClass(LearningActor actor, Long classId, Long subjectId) {
		if (actor.manager()) {
			return;
		}
		boolean allowed = sectionRepository.findBySchoolClassId(classId).stream().anyMatch(section -> subjectId == null
				? actor.teaches(section.getId())
				: actor.canWrite(section.getId(), subjectId));
		if (!allowed) {
			throw new AuthorizationDeniedException("You are not assigned to teach this class and subject");
		}
	}

	private Set<Long> teachingClassIds(LearningActor actor) {
		Set<Long> sectionIds = actor.teachingSectionIds();
		return sectionRepository.findAllById(sectionIds).stream().map(Section::getSchoolClass).map(SchoolClass::getId)
				.collect(Collectors.toSet());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
