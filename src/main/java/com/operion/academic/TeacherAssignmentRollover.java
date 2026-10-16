package com.operion.academic;

import java.util.ArrayList;
import java.util.List;

import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.rollover.RolloverContributor;
import com.operion.organisation.rollover.RolloverOptions;
import com.operion.organisation.rollover.RolloverStep;
import com.operion.organisation.rollover.RolloverStep.Tally;
import org.springframework.stereotype.Component;

/**
 * Active teacher assignments, attached to the matching section of the new year and starting on
 * its first day. A teacher whose staff record shows they have left is left out and named, so
 * the new year never starts with someone assigned who is no longer there.
 */
@Component
public class TeacherAssignmentRollover implements RolloverContributor {

	private final TeacherAssignmentRepository assignmentRepository;
	private final YearStructureLookup lookup;
	private final StaffProfileRepository staffProfileRepository;

	TeacherAssignmentRollover(TeacherAssignmentRepository assignmentRepository, YearStructureLookup lookup,
			StaffProfileRepository staffProfileRepository) {
		this.assignmentRepository = assignmentRepository;
		this.lookup = lookup;
		this.staffProfileRepository = staffProfileRepository;
	}

	@Override
	public String key() {
		return "TEACHER_ASSIGNMENTS";
	}

	@Override
	public String label() {
		return "Teacher assignments";
	}

	@Override
	public AppModule module() {
		return AppModule.ACADEMICS;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public RolloverStep run(AcademicYear source, AcademicYear target, RolloverOptions options) {
		Tally assignments = new Tally("Assignments");
		int noSection = 0;
		int left = 0;
		int conflicts = 0;
		for (TeacherAssignment assignment : assignmentRepository.findByAcademicYearIdAndStatus(source.getId(), TeacherAssignmentStatus.ACTIVE)) {
			Section section = lookup.sectionIn(target, assignment.getSection()).orElse(null);
			if (section == null) {
				noSection++;
				continue;
			}
			Long subjectId = assignment.getSubject() == null ? null : assignment.getSubject().getId();
			Long teacherId = assignment.getTeacherPerson().getId();
			if (assignmentRepository.existsBySectionIdAndSubjectIdAndTeacherPersonIdAndStatus(section.getId(), subjectId, teacherId,
					TeacherAssignmentStatus.ACTIVE)) {
				assignments.alreadyThere();
				continue;
			}
			if (staffProfileRepository.findByPersonId(teacherId).map(staff -> staff.getStatus() == StaffProfileStatus.RESIGNED
					|| staff.getStatus() == StaffProfileStatus.TERMINATED).orElse(false)) {
				left++;
				continue;
			}
			if (holderAlreadyThere(section, assignment, subjectId)) {
				conflicts++;
				continue;
			}
			assignmentRepository.save(new TeacherAssignment(target, section, assignment.getSubject(), assignment.getTeacherPerson(),
					assignment.getAssignmentType(), target.getStartDate()));
			assignments.copied();
		}
		List<String> notes = new ArrayList<>();
		if (noSection > 0) {
			notes.add(noSection + " assignment(s) left out: their class or section does not exist in the new year yet (run the classes step first)");
		}
		if (left > 0) {
			notes.add(left + " assignment(s) left out: the teacher has resigned or been terminated");
		}
		if (conflicts > 0) {
			notes.add(conflicts + " assignment(s) left out: the section already has a different teacher in that role");
		}
		return RolloverStep.of(this, List.of(assignments), notes);
	}

	/** Homeroom and subject assignments hold one teacher per section (and subject); a co-teacher may sit beside them. */
	private boolean holderAlreadyThere(Section section, TeacherAssignment source, Long subjectId) {
		return switch (source.getAssignmentType()) {
			case HOMEROOM -> assignmentRepository.findBySectionIdAndAssignmentTypeAndStatus(
					section.getId(), TeacherAssignmentType.HOMEROOM, TeacherAssignmentStatus.ACTIVE).isPresent();
			case SUBJECT -> subjectId != null && assignmentRepository.findBySectionIdAndSubjectIdAndAssignmentTypeAndStatus(
					section.getId(), subjectId, TeacherAssignmentType.SUBJECT, TeacherAssignmentStatus.ACTIVE).isPresent();
			case CO_TEACHER -> false;
		};
	}
}
