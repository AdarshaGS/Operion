package com.operion.academic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.rollover.RolloverContributor;
import com.operion.organisation.rollover.RolloverOptions;
import com.operion.organisation.rollover.RolloverStep;
import com.operion.organisation.rollover.RolloverStep.Tally;
import org.springframework.stereotype.Component;

/** Classes, their sections and the subjects taught in each - the skeleton every other step hangs off. Active rows only. */
@Component
public class ClassStructureRollover implements RolloverContributor {

	private final SchoolClassRepository classRepository;
	private final SectionRepository sectionRepository;
	private final ClassSubjectRepository classSubjectRepository;

	ClassStructureRollover(SchoolClassRepository classRepository, SectionRepository sectionRepository,
			ClassSubjectRepository classSubjectRepository) {
		this.classRepository = classRepository;
		this.sectionRepository = sectionRepository;
		this.classSubjectRepository = classSubjectRepository;
	}

	@Override
	public String key() {
		return "CLASSES";
	}

	@Override
	public String label() {
		return "Classes, sections and subjects";
	}

	@Override
	public AppModule module() {
		return AppModule.ACADEMICS;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public RolloverStep run(AcademicYear source, AcademicYear target, RolloverOptions options) {
		Tally classes = new Tally("Classes");
		Tally sections = new Tally("Sections");
		Tally subjects = new Tally("Class subjects");
		List<String> notes = new ArrayList<>();

		for (SchoolClass sourceClass : classRepository.findByAcademicYearId(source.getId())) {
			if (sourceClass.getStatus() != SchoolClassStatus.ACTIVE) {
				continue;
			}
			SchoolClass targetClass = classRepository.findByAcademicYearIdAndCampusIdAndGradeLevelId(
					target.getId(), sourceClass.getCampus().getId(), sourceClass.getGradeLevel().getId()).orElse(null);
			if (targetClass != null) {
				classes.alreadyThere();
			} else if (sourceClass.getDisplayName() != null
					&& classRepository.findByAcademicYearIdAndDisplayNameIgnoreCase(target.getId(), sourceClass.getDisplayName()).isPresent()) {
				notes.add("Class \"" + sourceClass.getDisplayName() + "\" left out: the new year already has a different class with that name");
				continue;
			} else {
				targetClass = classRepository.save(new SchoolClass(target, sourceClass.getCampus(), sourceClass.getGradeLevel(), sourceClass.getDisplayName()));
				classes.copied();
			}

			for (Section section : sectionRepository.findBySchoolClassId(sourceClass.getId())) {
				if (section.getStatus() != SectionStatus.ACTIVE) {
					continue;
				}
				if (sectionRepository.findBySchoolClassIdAndNameIgnoreCase(targetClass.getId(), section.getName()).isPresent()) {
					sections.alreadyThere();
				} else {
					sectionRepository.save(new Section(targetClass, section.getName(), section.getCapacity(), section.getRoom()));
					sections.copied();
				}
			}

			Set<Long> subjectsInTarget = classSubjectRepository.findBySchoolClassId(targetClass.getId()).stream()
					.map(classSubject -> classSubject.getSubject().getId()).collect(Collectors.toSet());
			for (ClassSubject classSubject : classSubjectRepository.findBySchoolClassId(sourceClass.getId())) {
				if (classSubject.getStatus() != ClassSubjectStatus.ACTIVE) {
					continue;
				}
				if (subjectsInTarget.contains(classSubject.getSubject().getId())) {
					subjects.alreadyThere();
				} else {
					classSubjectRepository.save(new ClassSubject(targetClass, classSubject.getSubject(), classSubject.isMandatory()));
					subjects.copied();
				}
			}
		}
		return RolloverStep.of(this, List.of(classes, sections, subjects), notes);
	}
}
