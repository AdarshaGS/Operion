package com.operion.communication;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.identity.Person;
import com.operion.organisation.Campus;
import com.operion.parent.StudentGuardianRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns announcement authoring/publish/cancel, audience fan-out into
 * NotificationRecipient rows, and per-person notification preferences. Fan-out for
 * CLASS/SECTION/INDIVIDUAL targets students' own Person (if they have a login) plus
 * their guardians' Persons - it deliberately does not include teachers assigned to the
 * class; that's a v2 scope call, not an oversight.
 */
@Service
public class CommunicationService {

	private final AnnouncementRepository announcementRepository;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final NotificationRecipientRepository notificationRecipientRepository;
	private final NotificationPreferenceRepository notificationPreferenceRepository;
	private final OrganisationMembershipRepository organisationMembershipRepository;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentRepository studentRepository;
	private final StudentGuardianRepository studentGuardianRepository;

	public CommunicationService(AnnouncementRepository announcementRepository,
			NotificationTemplateRepository notificationTemplateRepository,
			NotificationRecipientRepository notificationRecipientRepository,
			NotificationPreferenceRepository notificationPreferenceRepository,
			OrganisationMembershipRepository organisationMembershipRepository,
			SectionRepository sectionRepository,
			StudentEnrollmentRepository studentEnrollmentRepository,
			StudentRepository studentRepository,
			StudentGuardianRepository studentGuardianRepository) {
		this.announcementRepository = announcementRepository;
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.notificationPreferenceRepository = notificationPreferenceRepository;
		this.organisationMembershipRepository = organisationMembershipRepository;
		this.sectionRepository = sectionRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentRepository = studentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
	}

	public Announcement createAnnouncement(Campus campus, String title, String body, AudienceType audienceType, Long audienceRefId) {
		return announcementRepository.save(new Announcement(campus, title, body, audienceType, audienceRefId));
	}

	/** Resolves the audience once, filters by preference, and writes the recipient snapshot - see class doc. */
	@Transactional
	public Announcement publishAnnouncement(Announcement announcement) {
		announcement.publish();
		Announcement published = announcementRepository.save(announcement);

		for (Person person : resolveAudience(published)) {
			if (isChannelEnabled(person, NotificationChannel.IN_APP)) {
				notificationRecipientRepository.save(new NotificationRecipient(published, person, NotificationChannel.IN_APP));
			}
		}
		return published;
	}

	public Announcement cancelAnnouncement(Announcement announcement) {
		announcement.cancel();
		return announcementRepository.save(announcement);
	}

	public NotificationTemplate createTemplate(String code, NotificationChannel channel, String subjectTemplate, String bodyTemplate) {
		return notificationTemplateRepository.save(new NotificationTemplate(code, channel, subjectTemplate, bodyTemplate));
	}

	/** For a module (Fees, Attendance, ...) to fire a templated system notification at an explicit set of persons. */
	@Transactional
	public List<NotificationRecipient> sendTemplatedNotification(NotificationTemplate template, List<Person> recipients) {
		return recipients.stream()
				.filter(person -> isChannelEnabled(person, template.getChannel()))
				.map(person -> notificationRecipientRepository.save(new NotificationRecipient(null, person, template.getChannel())))
				.toList();
	}

	public NotificationRecipient markRead(NotificationRecipient recipient) {
		recipient.markRead();
		return notificationRecipientRepository.save(recipient);
	}

	/** Upserts - one row per (person, channel), created on first preference change. */
	@Transactional
	public NotificationPreference setPreference(Person person, NotificationChannel channel, boolean enabled) {
		return notificationPreferenceRepository.findByPersonIdAndChannel(person.getId(), channel)
				.map(existing -> {
					existing.setEnabled(enabled);
					return notificationPreferenceRepository.save(existing);
				})
				.orElseGet(() -> notificationPreferenceRepository.save(new NotificationPreference(person, channel, enabled)));
	}

	private boolean isChannelEnabled(Person person, NotificationChannel channel) {
		return notificationPreferenceRepository.findByPersonIdAndChannel(person.getId(), channel)
				.map(NotificationPreference::isEnabled)
				.orElse(true);
	}

	private Set<Person> resolveAudience(Announcement announcement) {
		return switch (announcement.getAudienceType()) {
			case ORG -> membershipPersons(organisationMembershipRepository.findByStatus(MembershipStatus.ACTIVE));
			case CAMPUS -> membershipPersons(
					organisationMembershipRepository.findByCampusIdAndStatus(requireCampus(announcement).getId(), MembershipStatus.ACTIVE));
			case CLASS -> sectionRepository.findBySchoolClassId(announcement.getAudienceRefId()).stream()
					.map(Section::getId)
					.flatMap(sectionId -> studentEnrollmentRepository.findBySectionIdAndCurrentTrue(sectionId).stream())
					.collect(collectStudentAndGuardianPersons());
			case SECTION -> studentEnrollmentRepository.findBySectionIdAndCurrentTrue(announcement.getAudienceRefId()).stream()
					.collect(collectStudentAndGuardianPersons());
			case INDIVIDUAL -> {
				Student student = studentRepository.findById(announcement.getAudienceRefId())
						.orElseThrow(() -> new IllegalArgumentException("No student with id " + announcement.getAudienceRefId()));
				yield studentAndGuardianPersons(student);
			}
		};
	}

	private Collector<StudentEnrollment, ?, Set<Person>> collectStudentAndGuardianPersons() {
		return Collectors.flatMapping(
				enrollment -> studentAndGuardianPersons(enrollment.getStudent()).stream(),
				Collectors.toCollection(LinkedHashSet::new));
	}

	private Set<Person> studentAndGuardianPersons(Student student) {
		Set<Person> persons = new LinkedHashSet<>();
		persons.add(student.getPerson());
		studentGuardianRepository.findByStudentId(student.getId())
				.forEach(studentGuardian -> persons.add(studentGuardian.getGuardian().getPerson()));
		return persons;
	}

	private Set<Person> membershipPersons(List<OrganisationMembership> memberships) {
		Set<Person> persons = new LinkedHashSet<>();
		memberships.forEach(membership -> persons.add(membership.getPerson()));
		return persons;
	}

	private Campus requireCampus(Announcement announcement) {
		if (announcement.getCampus() == null) {
			throw new IllegalStateException("Announcement " + announcement.getId() + " has CAMPUS audience but no campus set");
		}
		return announcement.getCampus();
	}
}
