package com.operion.learning;

import com.operion.academic.ClassSubjectStatus;
import com.operion.academic.ClassSubjectRepository;
import com.operion.common.InvalidRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Homework (#226). Every method takes the resolved {@link LearningActor}, so an admin and a
 * teacher share one code path and the section/subject scope check is never skipped.
 */
@Service
@Transactional
public class HomeworkService {

	/** Class-level rollup: how many of the section's currently enrolled students have submitted. */
	public record HomeworkView(Homework homework, int enrolled, int submitted, int reviewed) {
	}

	/** One row of the per-homework tracking sheet; submission is null for a student who hasn't submitted. */
	public record TrackingRow(StudentEnrollment enrollment, HomeworkSubmission submission) {
	}

	private final HomeworkRepository homeworkRepository;
	private final HomeworkSubmissionRepository submissionRepository;
	private final SectionRepository sectionRepository;
	private final SubjectRepository subjectRepository;
	private final PersonRepository personRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final ClassSubjectRepository classSubjectRepository;

	public HomeworkService(HomeworkRepository homeworkRepository, HomeworkSubmissionRepository submissionRepository,
			SectionRepository sectionRepository, SubjectRepository subjectRepository, PersonRepository personRepository,
			StudentRepository studentRepository, StudentEnrollmentRepository enrollmentRepository,
			ClassSubjectRepository classSubjectRepository) {
		this.classSubjectRepository = classSubjectRepository;
		this.homeworkRepository = homeworkRepository;
		this.submissionRepository = submissionRepository;
		this.sectionRepository = sectionRepository;
		this.subjectRepository = subjectRepository;
		this.personRepository = personRepository;
		this.studentRepository = studentRepository;
		this.enrollmentRepository = enrollmentRepository;
	}

	public Homework assign(LearningActor actor, Long sectionId, Long subjectId, String title, String description,
			LocalDate dueDate, String attachmentRef) {
		if (title == null || title.isBlank()) {
			throw new InvalidRequestException("Title is required");
		}
		if (dueDate == null) {
			throw new InvalidRequestException("Due date is required");
		}
		if (sectionId == null || subjectId == null) {
			throw new InvalidRequestException("A section and a subject are required");
		}
		actor.requireWrite(sectionId, subjectId);
		Section section = sectionRepository.findById(sectionId)
				.orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));
		boolean onClass = classSubjectRepository.findBySchoolClassId(section.getSchoolClass().getId()).stream()
				.anyMatch(cs -> cs.getStatus() == ClassSubjectStatus.ACTIVE && cs.getSubject().getId().equals(subjectId));
		if (!onClass) {
			throw new InvalidRequestException("The class does not take " + subject.getName());
		}
		Person assignedBy = personRepository.findById(actor.personId()).orElseThrow();
		return homeworkRepository.save(new Homework(section, subject, title.trim(), blankToNull(description), dueDate,
				blankToNull(attachmentRef), assignedBy));
	}

	/** The create response reports the same rollup the list does, so a new row and a refreshed one agree. */
	@Transactional(readOnly = true)
	public HomeworkView viewOf(Homework homework) {
		return rollup(List.of(homework)).get(0);
	}

	public Homework cancel(LearningActor actor, Long homeworkId) {
		Homework homework = requireWritable(actor, homeworkId);
		homework.cancel();
		return homework;
	}

	/** Newest due date first; a teacher sees only their own sections, an admin/viewer sees every section. */
	@Transactional(readOnly = true)
	public List<HomeworkView> list(LearningActor actor, Long sectionId) {
		List<Homework> homework;
		if (sectionId != null) {
			actor.requireReadSection(sectionId);
			homework = homeworkRepository.findBySectionIdOrderByDueDateDescIdDesc(sectionId);
		} else if (actor.seesAllSections()) {
			homework = homeworkRepository.findAllByOrderByDueDateDescIdDesc();
		} else {
			homework = homeworkRepository.findBySectionIdInOrderByDueDateDescIdDesc(actor.teachingSectionIds());
		}
		return rollup(homework);
	}

	@Transactional(readOnly = true)
	public List<TrackingRow> tracking(LearningActor actor, Long homeworkId) {
		Homework homework = homeworkRepository.findById(homeworkId)
				.orElseThrow(() -> new IllegalArgumentException("Homework not found: " + homeworkId));
		actor.requireReadSection(homework.getSection().getId());
		Map<Long, HomeworkSubmission> byStudent = submissionRepository.findByHomeworkId(homeworkId).stream()
				.collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
		return enrollmentRepository.findBySectionIdAndCurrentTrue(homework.getSection().getId()).stream()
				.sorted(Comparator.comparing(e -> e.getRollNumber() == null ? Integer.MAX_VALUE : e.getRollNumber()))
				.map(e -> new TrackingRow(e, byStudent.get(e.getStudent().getId())))
				.toList();
	}

	public HomeworkSubmission review(LearningActor actor, Long submissionId, String feedback) {
		HomeworkSubmission submission = submissionRepository.findById(submissionId)
				.orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
		Homework homework = submission.getHomework();
		actor.requireWrite(homework.getSection().getId(), homework.getSubject().getId());
		submission.review(blankToNull(feedback), Instant.now());
		return submission;
	}

	// --- guardian side (on behalf of a linked child) ---

	/** Active homework for the child's current section, newest due date first. */
	@Transactional(readOnly = true)
	public List<Homework> forChild(StudentEnrollment enrollment) {
		return homeworkRepository.findBySectionIdOrderByDueDateDescIdDesc(enrollment.getSection().getId()).stream()
				.filter(h -> h.getStatus() == HomeworkStatus.ACTIVE)
				.toList();
	}

	@Transactional(readOnly = true)
	public Map<Long, HomeworkSubmission> submissionsOfChild(Long studentId) {
		return submissionRepository.findByStudentId(studentId).stream()
				.collect(Collectors.toMap(s -> s.getHomework().getId(), Function.identity()));
	}

	public HomeworkSubmission submitForChild(ChildAccessService.OwnChild child, Long homeworkId, String text,
			String attachmentRef) {
		Homework homework = homeworkRepository.findById(homeworkId)
				.orElseThrow(() -> new IllegalArgumentException("Homework not found: " + homeworkId));
		StudentEnrollment enrollment = child.enrollment();
		if (!homework.getSection().getId().equals(enrollment.getSection().getId())) {
			throw new IllegalArgumentException("Homework not found: " + homeworkId);
		}
		if (homework.getStatus() != HomeworkStatus.ACTIVE) {
			throw new IllegalStateException("This homework has been cancelled");
		}
		if ((text == null || text.isBlank()) && (attachmentRef == null || attachmentRef.isBlank())) {
			throw new InvalidRequestException("Provide an answer or an attachment");
		}
		Instant now = Instant.now();
		boolean late = now.isAfter(homework.getDueDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
		Long studentId = enrollment.getStudent().getId();
		HomeworkSubmission existing = submissionRepository.findByHomeworkIdAndStudentId(homeworkId, studentId).orElse(null);
		Person by = personRepository.findById(child.guardianPersonId()).orElseThrow();
		if (existing != null) {
			existing.resubmit(by, blankToNull(text), blankToNull(attachmentRef), now, late);
			return existing;
		}
		return submissionRepository.save(new HomeworkSubmission(homework, studentRepository.findById(studentId).orElseThrow(), by,
				blankToNull(text), blankToNull(attachmentRef), now, late));
	}

	private Homework requireWritable(LearningActor actor, Long homeworkId) {
		Homework homework = homeworkRepository.findById(homeworkId)
				.orElseThrow(() -> new IllegalArgumentException("Homework not found: " + homeworkId));
		actor.requireWrite(homework.getSection().getId(), homework.getSubject().getId());
		return homework;
	}

	private List<HomeworkView> rollup(List<Homework> homework) {
		if (homework.isEmpty()) {
			return List.of();
		}
		Map<Long, List<HomeworkSubmission>> submissions = submissionRepository
				.findByHomeworkIdIn(homework.stream().map(Homework::getId).toList()).stream()
				.collect(Collectors.groupingBy(s -> s.getHomework().getId()));
		Map<Long, Integer> enrolledBySection = homework.stream().map(h -> h.getSection().getId()).distinct()
				.collect(Collectors.toMap(Function.identity(),
						id -> enrollmentRepository.findBySectionIdAndCurrentTrue(id).size()));
		return homework.stream().map(h -> {
			List<HomeworkSubmission> subs = submissions.getOrDefault(h.getId(), List.of());
			int reviewed = (int) subs.stream().filter(s -> s.getStatus() == HomeworkSubmissionStatus.REVIEWED).count();
			return new HomeworkView(h, enrolledBySection.get(h.getSection().getId()), subs.size(), reviewed);
		}).toList();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
