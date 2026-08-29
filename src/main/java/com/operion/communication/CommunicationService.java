package com.operion.communication;

import java.time.Instant;
import java.util.Arrays;
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
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.hr.StaffProfileStatus;
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
	private final StaffProfileRepository staffProfileRepository;
	private final AnnouncementAudienceMemberRepository announcementAudienceMemberRepository;

	public CommunicationService(AnnouncementRepository announcementRepository,
			NotificationTemplateRepository notificationTemplateRepository,
			NotificationRecipientRepository notificationRecipientRepository,
			NotificationPreferenceRepository notificationPreferenceRepository,
			OrganisationMembershipRepository organisationMembershipRepository,
			SectionRepository sectionRepository,
			StudentEnrollmentRepository studentEnrollmentRepository,
			StudentRepository studentRepository,
			StudentGuardianRepository studentGuardianRepository,
			StaffProfileRepository staffProfileRepository,
			AnnouncementAudienceMemberRepository announcementAudienceMemberRepository) {
		this.announcementRepository = announcementRepository;
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.notificationPreferenceRepository = notificationPreferenceRepository;
		this.organisationMembershipRepository = organisationMembershipRepository;
		this.sectionRepository = sectionRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentRepository = studentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.announcementAudienceMemberRepository = announcementAudienceMemberRepository;
	}

	public Announcement createAnnouncement(Campus campus, String title, String body, AudienceType audienceType, Long audienceRefId) {
		return createAnnouncement(campus, title, body, audienceType, audienceRefId, List.of(), null);
	}

	/** SELECTED_GROUP variant - persons is the ad-hoc audience, written as
	 * AnnouncementAudienceMember rows once the announcement has an id. */
	@Transactional
	public Announcement createAnnouncement(Campus campus, String title, String body, AudienceType audienceType, Long audienceRefId,
			List<Person> selectedGroupPersons) {
		return createAnnouncement(campus, title, body, audienceType, audienceRefId, selectedGroupPersons, null);
	}

	/** scheduledAt variant - a non-null value skips the manual publish step; see
	 * ScheduledAnnouncementPublisher, which auto-publishes once it's in the past. */
	@Transactional
	public Announcement createAnnouncement(Campus campus, String title, String body, AudienceType audienceType, Long audienceRefId,
			List<Person> selectedGroupPersons, Instant scheduledAt) {
		Announcement announcement = announcementRepository.save(new Announcement(campus, title, body, audienceType, audienceRefId, scheduledAt));
		for (Person person : selectedGroupPersons) {
			announcementAudienceMemberRepository.save(new AnnouncementAudienceMember(announcement, person));
		}
		return announcement;
	}

	/** Resolves the audience once, filters by preference, and fans out across every usable
	 * channel - IN_APP lands SENT immediately (row creation is delivery for it); an
	 * EMAIL/SMS row is only created when the person actually has that contact info, and
	 * starts PENDING for NotificationDispatchWorker/Service to actually deliver. */
	@Transactional
	public Announcement publishAnnouncement(Announcement announcement) {
		announcement.publish();
		Announcement published = announcementRepository.save(announcement);

		for (Person person : resolveAudience(published)) {
			fanOutToPerson(published, person);
		}
		return published;
	}

	private void fanOutToPerson(Announcement announcement, Person person) {
		for (NotificationChannel channel : NotificationChannel.values()) {
			if (shouldFanOut(person, channel)) {
				String subject = channel == NotificationChannel.SMS ? null : announcement.getTitle();
				notificationRecipientRepository.save(
						new NotificationRecipient(announcement, person, channel, subject, announcement.getBody()));
			}
		}
	}

	/** Preview for the compose screen, before an Announcement is even saved - see
	 * AnnouncementController's preview-audience endpoint. audienceSize is everyone the
	 * audience selection resolves to; notifiableCount is the subset who'd actually get at
	 * least one NotificationRecipient row, i.e. the same per-channel preference-and-usable
	 * check publishAnnouncement/fanOutToPerson applies. selectedGroupPersons is only read
	 * for SELECTED_GROUP. */
	@Transactional(readOnly = true)
	public AudiencePreview previewAudience(Campus campus, AudienceType audienceType, Long audienceRefId, List<Person> selectedGroupPersons) {
		Set<Person> resolved = resolveAudience(campus, audienceType, audienceRefId, selectedGroupPersons);
		long notifiable = resolved.stream()
				.filter(person -> Arrays.stream(NotificationChannel.values()).anyMatch(channel -> shouldFanOut(person, channel)))
				.count();
		return new AudiencePreview(resolved.size(), (int) notifiable);
	}

	public record AudiencePreview(int audienceSize, int notifiableCount) {
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
				.filter(person -> shouldFanOut(person, template.getChannel()))
				.map(person -> notificationRecipientRepository.save(
						new NotificationRecipient(null, person, template.getChannel(), template.getSubjectTemplate(), template.getBodyTemplate())))
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

	/** Whether a channel is even usable for this person - IN_APP always is; EMAIL/SMS need
	 * the corresponding contact field actually on file, so a person with no email/phone
	 * doesn't get a NotificationRecipient row that's a guaranteed dispatch failure. */
	private boolean channelIsUsable(Person person, NotificationChannel channel) {
		return switch (channel) {
			case IN_APP -> true;
			case EMAIL -> hasAddress(person.getEmail());
			case SMS -> hasAddress(person.getPhone());
		};
	}

	private boolean hasAddress(String value) {
		return value != null && !value.isBlank();
	}

	private boolean shouldFanOut(Person person, NotificationChannel channel) {
		return isChannelEnabled(person, channel) && channelIsUsable(person, channel);
	}

	private Set<Person> resolveAudience(Announcement announcement) {
		List<Person> selectedGroupPersons = announcement.getAudienceType() != AudienceType.SELECTED_GROUP ? List.of()
				: announcementAudienceMemberRepository.findByAnnouncementId(announcement.getId()).stream()
						.map(AnnouncementAudienceMember::getPerson)
						.toList();
		return resolveAudience(announcement.getCampus(), announcement.getAudienceType(), announcement.getAudienceRefId(), selectedGroupPersons);
	}

	private Set<Person> resolveAudience(Campus campus, AudienceType audienceType, Long audienceRefId, List<Person> selectedGroupPersons) {
		return switch (audienceType) {
			case ORG -> membershipPersons(organisationMembershipRepository.findByStatus(MembershipStatus.ACTIVE));
			case CAMPUS -> membershipPersons(
					organisationMembershipRepository.findByCampusIdAndStatus(requireCampus(campus).getId(), MembershipStatus.ACTIVE));
			case CLASS -> sectionRepository.findBySchoolClassId(audienceRefId).stream()
					.map(Section::getId)
					.flatMap(sectionId -> studentEnrollmentRepository.findBySectionIdAndCurrentTrue(sectionId).stream())
					.collect(collectStudentAndGuardianPersons());
			case SECTION -> studentEnrollmentRepository.findBySectionIdAndCurrentTrue(audienceRefId).stream()
					.collect(collectStudentAndGuardianPersons());
			case INDIVIDUAL -> {
				Student student = studentRepository.findById(audienceRefId)
						.orElseThrow(() -> new IllegalArgumentException("No student with id " + audienceRefId));
				yield studentAndGuardianPersons(student);
			}
			case STAFF -> staffPersons(staffProfileRepository.findByStatus(StaffProfileStatus.ACTIVE));
			case STAFF_MEMBER -> {
				StaffProfile staffProfile = staffProfileRepository.findById(audienceRefId)
						.orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + audienceRefId));
				yield Set.of(staffProfile.getPerson());
			}
			case SELECTED_GROUP -> new LinkedHashSet<>(selectedGroupPersons);
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

	private Set<Person> staffPersons(List<StaffProfile> staffProfiles) {
		Set<Person> persons = new LinkedHashSet<>();
		staffProfiles.forEach(staffProfile -> persons.add(staffProfile.getPerson()));
		return persons;
	}

	private Campus requireCampus(Campus campus) {
		if (campus == null) {
			throw new IllegalStateException("CAMPUS audience requires a campus");
		}
		return campus;
	}
}
